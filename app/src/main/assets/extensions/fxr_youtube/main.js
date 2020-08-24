'use strict';
const CUSTOM_USER_AGENT = 'Mozilla/5.0 (Linux; Android 7.1.1; Quest) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/11.1.0.1.64.238873877 SamsungBrowser/4.0 Chrome/84.0.4147.125 Mobile VR Safari/537.36';
const LOGTAG = '[firefoxreality:webcompat:youtube]';
const VIDEO_PROJECTION_PARAM = 'mozVideoProjection';
const YT_SELECTORS = {
  disclaimer: '.yt-alert-message, yt-alert-message',
  player: '#movie_player',
  embedPlayer: '.html5-video-player',
  largePlayButton: '.ytp-large-play-button',
  thumbnail: '.ytp-cued-thumbnail-overlay-image',
  embedTitle: '.ytp-title-text',
  queueHandle: 'ytd-playlist-panel-video-renderer',
  playbackControls: '.ytp-chrome-bottom',
  overlays: '.videowall-endscreen, .ytp-upnext, .ytp-ce-element'
};
const ENABLE_LOGS = true;
const logDebug = (...args) => ENABLE_LOGS && console.log(LOGTAG, ...args);
const logError = (...args) => ENABLE_LOGS && console.error(LOGTAG, ...args);
const CHROME_CONTROLS_MIN_WIDTH = 480;

class YoutubeExtension {
    // We set a custom UA to force Youtube to display the most optimal
    // and high-resolution layout available for playback in a mobile VR browser.
    overrideUA() {
        Object.defineProperty(navigator, 'userAgent', {
            get: () => CUSTOM_USER_AGENT
        });
        logDebug(`Youtube UA overriden to: ${navigator.userAgent}`)
    }

    // If missing, inject a `<meta name="viewport">` tag to trigger YouTube's mobile layout.
    overrideViewport() {
        const content = `width=device-width;maximum-scale=1;minimum-scale=1;initial-scale=1;`;
        let viewport = document.querySelector('meta[name="viewport"]');
        if (viewport) {
            viewport.setAttribute('content', content);
        } else {
            const container = document.head || document.documentElement;
            container.insertAdjacentHTML('afterbegin', `<meta name="viewport" content="${content}"/>`);
        }
        logDebug(`Youtube viewport updated: ${window.innerWidth}x${window.innerHeight} `);
    }

    // Select a better youtube video quality
    overrideQuality() {
        logDebug('overrideQuality attempt');
        const player = this.getPlayer();
        if (!player) {
            logDebug('player not ready');
            return false;
        }
        const preferredLevels = this.getPreferredQualities();
        const currentLevel = player.getPlaybackQuality();
        logDebug(`Video getPlaybackQuality: ${currentLevel}`);

        let availableLevels = player.getAvailableQualityLevels();
        logDebug(`Video getAvailableQualityLevels: ${availableLevels}`);
        for (const level of preferredLevels) {
            if (availableLevels.indexOf(level) >= 0) {
                if (currentLevel !== level) {
                    player.setPlaybackQualityRange(level, level);
                    logDebug(`Video setPlaybackQualityRange: ${level}`);
                } else {
                    logDebug('Best quality already selected');
                }
                return true;
            }
        }
       return false;
    }

    overrideQualityRetry() {
        this.retry("overrideQuality", () => this.overrideQuality());
    }

    is360(text) {
        return text.includes('360');
    }

    is360Video() {
        const targets = [
            document.querySelector(YT_SELECTORS.disclaimer),
            document.querySelector(YT_SELECTORS.embedTitle)
        ];
        return targets.some((node) => node && this.is360(node.textContent));;
    }

    isStereo(text) {
        const words = text.toLowerCase().split(/\s+|\./);
        return words.includes('stereo') || words.includes('3d') || words.includes('vr');
    }

    isStereoVideo() {
        const targets = [
            document.querySelector(YT_SELECTORS.disclaimer),
            document.querySelector(YT_SELECTORS.embedTitle)
        ];
        return targets.some((node) => node && this.isStereo(node.textContent));;
    }

    // Automatically select a video projection if needed
    overrideVideoProjection() {
        if (!this.isWatchingPage()) {
            logDebug("is not watching page");
            return; // Only override projection in the Youtube watching page.
        }
        const qs = new URLSearchParams(window.location.search);
        if (qs.get(VIDEO_PROJECTION_PARAM)) {
            logDebug(`Video has already a video projection selected: ${qs.get(VIDEO_PROJECTION_PARAM)}`);
            this.updateVideoStyle();
            return;
        }
        // There is no standard API to detect video projection yet.
        // Try to infer it from the video disclaimer or title for now.
        if (this.is360Video()) {
            qs.set('mozVideoProjection', this.isStereoVideo() ? '360s_auto' : '360_auto');
            this.updateURL(qs);
            this.updateVideoStyle();
            logDebug(`Video projection set to: ${qs.get(VIDEO_PROJECTION_PARAM)}`);
        } else {
            logDebug(`Video is flat, no projection selected`);
        }
    }

    updateVideoStyle() {
        const video = this.getVideo();
        if (video) {
            video.classList.add('fxr-vr-video');
            logDebug('Added video projection style');
        }
    }

    overrideClick(event) {
        this.overrideVideoProjection();
        const player = this.getPlayer();
        if (!this.isWatchingPage() || !this.hasVideoProjection() || document.fullscreenElement || !player) {
            return; // Only override click in the Youtube watching page for 360 videos.
        }
        if (this.isEmbed() && !this.isVideoReady()) {
            return false; // Embeds videos are only loaded after the first click
        }
        const target = event.target;
        let valid = target.tagName.toLowerCase() === 'video' ||
            target === document.querySelector(YT_SELECTORS.thumbnail) ||
            target === document.querySelector(YT_SELECTORS.largePlayButton) ||
            target == player;

        if (valid) {
            player.playVideo();
            player.requestFullscreen();
            // Force video play when entering immersive mode.
            setTimeout(() => this.retry("PlayVideo", () => {
                player.playVideo();
                return !this.getVideo().paused;
            }), 200);
        }
    }

    // Fix for the draggable items to continue being draggable when a context menu is displayed.
    // https://github.com/MozillaReality/FirefoxReality/issues/2611
    queueContextMenuFix() {
        logDebug('queueContextMenuFix');
        const handles = document.querySelectorAll(YT_SELECTORS.queueHandle);
        for (var i=0; i<handles.length; i++) {
            handles[i].removeEventListener('contextmenu', this.onContextMenu);
            handles[i].addEventListener('contextmenu', this.onContextMenu);
        }
    }

    onContextMenu(event) {
        setTimeout(() => {
            var evt = document.createEvent("MouseEvents");
            evt.initEvent("mouseup", true, true);
            event.target.dispatchEvent(evt);
        });

        // This is supposed to prevent the context menu from showing but it doesn't seem to work
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        return false;
    }

    // Prevent the double click to reach the player to avoid double clicking
    // to trigger a playback forward/backward event.
    // https://github.com/MozillaReality/FirefoxReality/issues/2947
    videoControlsForwardFix() {
        logDebug('videoControlsForwardFix');
        const playbackControls = document.querySelector(YT_SELECTORS.playbackControls);
        playbackControls.removeEventListener("touchstart", this.videoControlsOnTouchStart);
        playbackControls.addEventListener("touchstart", this.videoControlsOnTouchStart);
        playbackControls.removeEventListener("touchend", this.videoControlsOnTouchEnd);
        playbackControls.addEventListener("touchend", this.videoControlsOnTouchEnd);
    }

    videoControlsOnTouchStart(evt) {
        evt.stopPropagation();
        return false;
    }

    videoControlsOnTouchEnd(evt) {
        evt.stopPropagation();
        return false;
    }

    playerControlsMarginFix() {
        if (youtube.isInFullscreen()) {
            if (window.innerHeight < CHROME_CONTROLS_MIN_WIDTH) {
              var of = CHROME_CONTROLS_MIN_WIDTH - window.innerHeight;
              console.log(of)
              document.querySelector(".ytp-chrome-bottom").style.setProperty("margin-bottom", `${of}px`)
            } else {
              document.querySelector(".ytp-chrome-bottom").style.removeProperty("margin-bottom")
            }

        } else {
            document.querySelector(".ytp-chrome-bottom").style.removeProperty("margin-bottom")
        }
    }

    playerFixes() {
        this.overrideVideoProjection();
        this.overrideQualityRetry();
        this.hideOverlaysFix();
        this.queueContextMenuFix();
        this.videoControlsForwardFix();
        this.playerControlsMarginFix();
    }

    // Runs the callback when the video is ready (has loaded the first frame).
    waitForVideoReady(callback) {
        this.retry("VideoReady", () => {
            const video = this.getVideo();
            if (!video) {
                return false;
            }
            if (video.readyState >= 2) {
              callback();
            } else {
              video.addEventListener("loadeddata", callback, {once: true});
            }
            return true;
       });
    }

     // Get's the Youtube player elements which contains the API functions.
    getPlayer() {
        let player = document.querySelector(this.isEmbed() ? YT_SELECTORS.embedPlayer : YT_SELECTORS.player);
        if (!player || !player.wrappedJSObject) {
            return null;
        }
        return player.wrappedJSObject;
    }

    getVideo() {
        return document.getElementsByTagName('video')[0];
    }

    // Get's the preferred video qualities for the current device.
    getPreferredQualities() {
        // Disable 5k video until issue can be resolved in Gecko Media Process
        // see https://github.com/MozillaReality/FirefoxReality/issues/3193
        // let all = ['hd2880', 'hd2160','hd1440', 'hd1080', 'hd720', 'large', 'medium'];
        let all = ['hd2160','hd1440', 'hd1080', 'hd720', 'large', 'medium'];
        return all;
    }

    // Returns true if we are in a video watching page.
    isWatchingPage() {
        return window.location.pathname.startsWith('/watch') || this.isEmbed();
    }

    isEmbed() {
        return window.location.pathname.startsWith('/embed');
    }

    // Returns true if we are in a video watching page.
    hasVideoProjection() {
        const qs = new URLSearchParams(window.location.search);
        return !!qs.get(VIDEO_PROJECTION_PARAM);
    }

    isVideoReady() {
        const video = this.getVideo();
        return video && video.readyState >=2;
    }

    // Hide overlays when in immersive mode
    // https://github.com/MozillaReality/FirefoxReality/issues/2673
    hideOverlaysFix() {
        if (youtube.is360Video() || youtube.isStereoVideo()) {
            logDebug('hideOverlaysFix');
            var overlays = document.querySelectorAll(YT_SELECTORS.overlays);
            var observer = new MutationObserver((mutations) => {
                if (youtube.isInFullscreen()) {
                    for (const mutation of mutations) {
                        if (mutation.target) {
                            mutation.target.style.display = 'none';
                        }
                    }
                }
            });
            for(const overlay of overlays) {
                observer.observe(overlay, { attributes: true });
            }
        }
    }

    isInFullscreen() {
        return !((document.fullScreenElement !== undefined && document.fullScreenElement === null) ||
         (document.msFullscreenElement !== undefined && document.msFullscreenElement === null) ||
         (document.mozFullScreen !== undefined && !document.mozFullScreen) ||
         (document.webkitIsFullScreen !== undefined && !document.webkitIsFullScreen));
    }

    // Utility function to retry tasks max n times until the execution is successful.
    retry(taskName, task, attempts = 10, interval = 200) {
        let succeeded = false;
        try {
            succeeded = task();
        } catch (ex) {
            logError(`Got exception running ${taskName} task: ${ex}`);
        }
        if (succeeded) {
            logDebug(`${taskName} succeeded`);
            return;
        }
        attempts--;
        logDebug(`${taskName} failed. Remaining attempts ${attempts}`);
        if (attempts > 0) {
            setTimeout(() => {
                this.retry(taskName, task, attempts, interval);
            })
        };
    }
    // Utility function to replace current URL params and update history.
    updateURL(qs) {
        const newUrl = `${window.location.pathname}?${qs}`;
        window.history.replaceState({}, document.title, newUrl);
        logDebug(`update URL to ${newUrl}`);
    }


}

logDebug(`Initializing youtube extension in frame: ${window.location.href}`);
const youtube = new YoutubeExtension();
youtube.overrideUA();
youtube.overrideViewport();
window.addEventListener('load', () => {
    logDebug('page load');
    // Wait until video has loaded the first frame to force quality change.
    // This prevents the infinite spinner problem.
    // See https://github.com/MozillaReality/FirefoxReality/issues/1433
    if (youtube.isWatchingPage()) {
        youtube.waitForVideoReady(() => youtube.playerFixes());
    }
});
window.addEventListener("resize", () => youtube.playerControlsMarginFix());
document.addEventListener("fullscreenchange", () => youtube.playerControlsMarginFix());
window.addEventListener('pushstate', () => youtube.overrideVideoProjection());
window.addEventListener('popstate', () => youtube.overrideVideoProjection());
window.addEventListener('click', event => youtube.overrideClick(event));
window.addEventListener('mouseup', event => youtube.queueContextMenuFix());
window.addEventListener("yt-navigate-finish", () => youtube.playerFixes());
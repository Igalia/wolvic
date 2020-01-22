'use strict';
const CUSTOM_USER_AGENT = 'Mozilla/5.0 (Linux; Android 7.1.1; Quest) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/7.0.13.186866463 SamsungBrowser/4.0 Chrome/77.0.3865.126 Mobile VR Safari/537.36';
const LOGTAG = '[firefoxreality:webcompat:youtube]';
const VIDEO_PROJECTION_PARAM = 'mozVideoProjection';
const YT_SELECTORS = {
  disclaimer: '.yt-alert-message, yt-alert-message',
  player: '#movie_player',
  embedPlayer: '.html5-video-player',
  largePlayButton: '.ytp-large-play-button',
  thumbnail: '.ytp-cued-thumbnail-overlay-image',
  embedTitle: '.ytp-title-text'
};
const ENABLE_LOGS = true;
const logDebug = (...args) => ENABLE_LOGS && console.log(LOGTAG, ...args);
const logError = (...args) => ENABLE_LOGS && console.error(LOGTAG, ...args);

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
        const targets = [
            document.querySelector(YT_SELECTORS.disclaimer),
            document.querySelector(YT_SELECTORS.embedTitle)
        ];
        const is360 = targets.some((node) => node && node.textContent.includes('360'));
        if (is360) {
            const stereo = targets.some((node) => node && node.textContent.toLowerCase().includes('stereo'));
            qs.set('mozVideoProjection', stereo ? '360s_auto' : '360_auto');
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
        let all = ['hd2880', 'hd2160','hd1440', 'hd1080', 'hd720', 'large', 'medium'];
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

    // Utility function to retry tasks max n times until the execution is successful.
    retry(taskName, task, attempts = 10, interval = 200) {
        let succeeded = false;
        try {
            succeeded = task();
        } catch (ex) {
            logError(`Got exception runnning ${taskName} task: ${ex}`);
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
    youtube.overrideVideoProjection();
    // Wait until video has loaded the first frame to force quality change.
    // This prevents the infinite spinner problem.
    // See https://github.com/MozillaReality/FirefoxReality/issues/1433
    if (youtube.isWatchingPage()) {
        youtube.waitForVideoReady(() => youtube.overrideQualityRetry());
    }
});

window.addEventListener('pushstate', () => youtube.overrideVideoProjection());
window.addEventListener('popstate', () => youtube.overrideVideoProjection());
window.addEventListener('click', event => youtube.overrideClick(event));

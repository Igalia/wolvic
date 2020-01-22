const FXR_PATCH_NAME = 'vimeo360';
let LOGTAG = `[firefoxreality:webcompat:vimeo] [patch:${FXR_PATCH_NAME}]`;
// const CUSTOM_USER_AGENT = 'Mozilla/5.0 (Linux; Android 7.1.1; Pacific Build) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/5.7.1.23.151668513 SamsungBrowser/4.0 Chrome/66.0.3359.203 Mobile VR Safari/537.36';
const VIMEO_CURRENT_PAGE = {
  hasVideo: /\/\d+/.test(window.location.pathname),
  isVideoDetailPage: false,
  isVideoEmbedPage: false
};
if (VIMEO_CURRENT_PAGE.hasVideo) {
  VIMEO_CURRENT_PAGE.isVideoDetailPage = window.location.hostname !== 'player.vimeo.com';
  VIMEO_CURRENT_PAGE.isVideoEmbedPage = window.location.hostname === 'player.vimeo.com';
}
const QS_DEFAULTS = {
  canvas: '1',
  mozVideoProjection: '360_auto'
};
const VIMEO_SCRIPT_REGEXS = {
  videoDetailPage: /window\.vimeo\.clip_page_config\s*=\s*/i,
  videoDetailPagePlayer: /window\.vimeo\.clip_page_config\.player\s*=\s*/i,
  videoEmbedPage: /.*config\s*=\s*{/i,
  videoIsSpatial: /.*(\\)?"is_spatial(\\)?"\s*:\s*true/i
};
if (VIMEO_CURRENT_PAGE.hasVideo) {
  LOGTAG += ' [video]';
}
if (VIMEO_CURRENT_PAGE.isVideoDetailPage) {
  LOGTAG += ' [page]';
} else if (VIMEO_CURRENT_PAGE.isVideoEmbedPage) {
  LOGTAG += ' [embed]';
} else {
  LOGTAG += ` [other]`;
}
LOGTAG += ` [${window.location.href}]`;

// Object.defineProperty(navigator, 'userAgent', {get: () => CUSTOM_USER_AGENT});

// TODO: Improve "Enter VR" click target by not requiring explicit clicks on the "Fullscren" icon.
// Hijack `playerArea` / playerContainer`, `embedPlayer`, etc. to call `requestFullscreen(…)`.

// const VIMEO_SELECTORS = {
//   player: '.js-player-fullscreen',
//   playerArea: '.player_area',
//   playerContainer: '.player_container',
//   embedPlayer: '#player',
//   embedVideo: 'video',
//   embedVideoTarget: '.vp-target',
//   embedVideoPlayButton: '.vp-controls .play'
// };

// TODO: Test SPA navigation.

let qs = new URLSearchParams(window.location.search);
const prefs = {
  log: qs.get('mozDebug') !== '0' && qs.get('mozdebug') !== '0' && qs.get('debug') !== '0',
  debug: qs.get('mozDebug') || qs.get('mozdebug') || qs.get('debug') || '0'
};

const log = (...args) => prefs.log && console.log(LOGTAG, ...args);
const logDebug = (...args) => prefs.log && prefs.debug && console.debug(LOGTAG, ...args);
const logError = (...args) => prefs.log && console.error(LOGTAG, ...args);
const logWarn = (...args) => prefs.log && console.warn(LOGTAG, ...args);

document.documentElement.setAttribute('data-fxr-debug', prefs.debug === '1');

log('Loaded WebCompat content script');

try {
  let is360 = false;

  logDebug(`Navigation type "${window.performance.navigation.type}"`);

  window.addEventListener('pushstate', evt => {
    logDebug(`Navigation type "${window.performance.navigation.type}"`);
    logDebug(`Event "${evt.type}" fired`);
    updateCurrentQsInUrl();
  });

  window.addEventListener('load', evt => {
    logDebug(`Navigation type "${window.performance.navigation.type}"`);
    logDebug(`Event "${evt.type}" fired`);
    updateCurrentQsInUrl();
    selectBetterQuality();
  });

  window.addEventListener('fullscreenchange', evt => {
    logDebug(`Event "${evt.type}" fired (current Fullscreen state: ${!!document.fullscreenElement})`);
    if (!VIMEO_CURRENT_PAGE.hasVideo) {
      return;
    }
    if (!document.fullscreenElement) {
      // When the user exits VR, change `&mozVideoProjection=360_auto` to `&mozVideoProjection=360` so FxR's
      // Movie-Mode Fullscreen Controls allow the user to change the Projection if they want the next time they
      // want to play the video.
      QS_DEFAULTS.mozVideoProjection = QS_DEFAULTS.mozVideoProjection.replace('_auto', '');
      updateCurrentQsInUrl();
    }
  });

  function getUrlWithNewQs (url) {
    const parsedUrl = new URL(url);

    let qs = new URLSearchParams(parsedUrl.search);

    qs.sort();
    const qsStrBefore = qs.toString();

    Object.keys(QS_DEFAULTS).forEach(key => {
      if (!qs.has(key)) {
        qs.set(key, QS_DEFAULTS[key]);
      }
    });
    if (!is360) {
      if (qs.get('canvas')) {
        qs.delete('canvas');
      }
      if ((qs.get('mozVideoProjection') || '').includes('360')) {
        qs.delete('mozVideoProjection');
      }
    }

    qs.sort();
    const qsStrAfter = qs.toString();

    if (qsStrBefore === qsStrAfter) {
      return {
        modified: false,
        url
      };
    }

    url = `${parsedUrl.origin}${parsedUrl.pathname}?${qs}${parsedUrl.hash || ''}`;

    return {
      modified: true,
      url
    };
  }

  function simulateTouch(item) {
    try {
      fakeTouch(item, "touchstart");
      fakeTouch(item, "touchend");
    }
    catch (err) {
      item.click();
    }
  }

  function fakeTouch(element, eventType) {
    var rect = element.getBoundingClientRect();
    const touchObj = new Touch({
      identifier: Date.now(),
      target: element,
      pageX: rect.x + 10 + window.pageXOffset,
      pageY: rect.y + 10 + window.pageYOffset,
      clientX: rect.x + 10,
      clientY: rect.y + 10,
      radiusX: 2.0,
      radiusY: 2.0,
      rotationAngle: 5,
      force: 0.5,
    });

    const touchEvent = new TouchEvent(eventType, {
      cancelable: true,
      bubbles: true,
      touches: [touchObj],
      targetTouches: [],
      changedTouches: [touchObj],
      shiftKey: true,
    });

    element.dispatchEvent(touchEvent);
  }


  function selectBetterQuality(attemptCount) {
    attemptCount = attemptCount || 0;
    log("Attempt to select quality");
    // Preferred qualities
    // TODO: add 4K on powerfull enough devices only
    const preferred = [
      '2K', '1080p', '720p'
    ]

    var btnSettings = document.querySelector(".vp-prefs");

    if (btnSettings && btnSettings.getAttribute("aria-expanded") !== "true") {
      // Focus page to settings button
      btnSettings.focus();
      // Show quality selector
      simulateTouch(btnSettings.wrappedJSObject);
      // Hide quality panel so there are no glitches
      let qualityPanel = document.querySelector(".vp-panel--quality");
      if (qualityPanel) {
        qualityPanel.style.display = "none";
      }
    }

    let items = document.querySelectorAll(".vp-panel-item");

    if (items.length === 0) {
        if (attemptCount > 20) {
          log("Max attempts to select quality exceeded. Cancel operation.");
          return;
        }
        // Retry after some delay
        setTimeout(selectBetterQuality.bind(null, attemptCount + 1, btnSettings), 50);
        return;
    }
    items = Array.from(items);
    log("Found " + items.length + " quality items");
    for (quality of preferred) {
      let item = items.find(el => el.textContent && el.textContent.indexOf(quality) >= 0);
      if (item) {
          log("Select quality: " + item.textContent);
          simulateTouch(item);
          if (btnSettings && btnSettings.getAttribute("aria-expanded") === "true") {
            // Hide quality selector, some delay required
            let qualityPanel = document.querySelector(".vp-panel--quality");
            setTimeout(function() {
              simulateTouch(btnSettings.wrappedJSObject);
              setTimeout(function() {
                // Make quality panel visible after some delay so there are not animation glitches.
                if (qualityPanel) {
                  qualityPanel.style.display = "";
                }
              }, 200);
            }, 200);
          }
          return;
      }
    }

    log("No suitable quality found");
  }

  function updateCurrentQsInUrl (url) {
    url = url || window.location.href;
    const urlWithNewQs = getUrlWithNewQs(url);

    if (!urlWithNewQs.modified) {
      logDebug('Preserving query-string values in URL:', url);
      return;
    }

    const newUrl = urlWithNewQs.url;
    log('Updating query-string values in URL:', url, '->', newUrl);
    window.history.replaceState({}, document.title, newUrl);
    return true;
  }

  // Note: See docs on `beforescriptexecute`, a Gecko-proprietary event:
  // - https://bugzilla.mozilla.org/show_bug.cgi?id=587931
  // - https://developer.mozilla.org/en-US/docs/Web/API/Element/beforescriptexecute_event
  // - https://github.com/whatwg/html/commit/69f83cf
  // - https://github.com/whatwg/html/issues/943
  // - https://github.com/whatwg/html/pull/1103

  if (VIMEO_CURRENT_PAGE.isVideoDetailPage) {
    logDebug('On Vimeo video-detail page');

    window.addEventListener('beforescriptexecute', patchBeforeScriptExecute);

    function patchBeforeScriptExecute (evt) {
      logDebug('Event "patchBeforeScriptExecute" fired');

      try {
        const target = evt.target;

        const scriptText = target && target.textContent;
        // Look for only unpatched inline <script>s with a line that contains
        // `window.vimeo.clip_page_config = {`, which contains the values for the Vimeo player.
        if (!scriptText ||
            (!VIMEO_SCRIPT_REGEXS.videoDetailPage.test(scriptText) &&
             !VIMEO_SCRIPT_REGEXS.videoDetailPagePlayer.test(scriptText)) ||
            target.getAttribute('data-fxr-patched') === FXR_PATCH_NAME) {
          return;
        }

        log('Found inline script for two Vimeo player configs');

        if (document.querySelector(`script[data-fxr-patch-for~="${FXR_PATCH_NAME}"]`)) {
          // Already injected inline <script> to patch this JS script and any loaded in the future.
          evt.target.setAttribute('data-fxr-patched', FXR_PATCH_NAME);
          return;
        }

        log(`Preventing execution of and removing Vimeo script`);

        // Stop the external JS script from being executed, and remove the <script> from the page.
        evt.preventDefault();
        evt.stopPropagation();
        target.remove();

        let scriptTextLines = scriptText.split('\n');
        let line = '';
        let idx = 0;
        const objsToReplace = {};
        for (; idx < scriptTextLines.length; idx++) {
          line = scriptTextLines[idx];
          if (VIMEO_SCRIPT_REGEXS.videoDetailPage.test(line)) {
            objsToReplace[idx] = {
              name: 'window.vimeo.clip_page_config',
              str: line.trim().replace(/;*\s*window\.vimeo\.clip_page_config\s*=\s*/i, '').replace(/;*$/, '')
            };
          }
          if (VIMEO_SCRIPT_REGEXS.videoDetailPagePlayer.test(line)) {
            objsToReplace[idx] = {
              name: 'window.vimeo.clip_page_config.player',
              str: line.trim().replace(/;*\s*window\.vimeo\.clip_page_config\.player\s*=\s*/i, '').replace(/;*$/, '')
            };
          }
        }

        is360 = false;
        Object.keys(objsToReplace).forEach(lineIdx => {
          let newObjData = objsToReplace[lineIdx];
          let configObj = null;
          try {
            configObj = JSON.parse(newObjData.str);
          } catch (err) {
            logWarn('Could not parse line JSON from from JS variable assignment "config" for Vimeo player', err);
          }

          if (configObj) {
            log(`Parsing "${newObjData.name}" object`);

            const newConfigUrl = getUrlWithNewQs(configObj.config_url || configObj.player.config_url).url + '&canvas=1&transparent=1';

            if (newObjData.name === 'window.vimeo.clip_page_config') {
              if (configObj.clip.canvas === 1 || configObj.clip.is_spatial === true) {
                is360 = true;
                // Detect 360 stereo videos
                if (configObj.clip.title.toLowerCase().indexOf('stereo') >= 0) {
                    QS_DEFAULTS.mozVideoProjection = "360s_auto";
                }
              }

              configObj.clip.canvas = 1;
              configObj.clip.is_spatial = true;
              // NOTE: This tricks Vimeo's detail pages to not render the desktop-specific mouse-look controls in the video
              // player to render unwrapped 360° videos.
              configObj.clip.spatial = 0;

              configObj.player.config_url = newConfigUrl;
            } else if (newObjData.name === 'window.vimeo.clip_page_config.player') {
              configObj.config_url = newConfigUrl;
            }

            log('Updating Vimeo player-config URL:', newConfigUrl);

            scriptTextLines[lineIdx] = `; ${newObjData.name} = ${JSON.stringify(configObj, null, 2)};\n`;
          }
        });

        log(is360 ? 'Detected a 360 video' : 'Did not detect a 360 video');
        updateCurrentQsInUrl();
        document.documentElement.setAttribute('data-fxr-is-360', is360 ? 'true' : 'false');
        document.documentElement.setAttribute('data-fxr-video-page-layout', 'detail');

        scriptTextLines[idx] = line;

        // const newScriptText = `\nObject.defineProperty(navigator, 'userAgent', {get: () => '${CUSTOM_USER_AGENT}'});\n` +
        //   scriptTextLines.join('\n');
        const newScriptText = scriptTextLines.join('\n');

        // Inject the <script> back onto the page, but this time with the values of `config` changed.
        const scriptRedo = document.createElement('script');
        scriptRedo.textContent = newScriptText;
        scriptRedo.setAttribute('data-fxr-patched', FXR_PATCH_NAME);
        document.body.appendChild(scriptRedo);

        log('Re-injected inline script');

        window.removeEventListener('beforescriptexecute', patchBeforeScriptExecute);
      } catch (err) {
        log('Encountered error:', err);
      }
    }
  } else if (VIMEO_CURRENT_PAGE.isVideoEmbedPage) {
    logDebug('On Vimeo embedded-video-player page');

    window.addEventListener('beforescriptexecute', patchBeforeScriptExecute);
    // Note: See docs on `beforescriptexecute`, a Gecko-proprietary event:
    // - https://bugzilla.mozilla.org/show_bug.cgi?id=587931
    // - https://developer.mozilla.org/en-US/docs/Web/API/Element/beforescriptexecute_event
    // - https://github.com/whatwg/html/commit/69f83cf
    // - https://github.com/whatwg/html/issues/943
    // - https://github.com/whatwg/html/pull/1103

    function patchBeforeScriptExecute (evt) {
      log('Event "patchBeforeScriptExecute" fired');

      try {
        const target = evt.target;

        const scriptText = target && target.textContent;
        // Look for only unpatched inline <script>s with a line that contains `config = {`,
        // which contains the values for the Vimeo player.
        if (!scriptText || !VIMEO_SCRIPT_REGEXS.videoEmbedPage.test(scriptText) ||
            target.getAttribute('data-fxr-patched') === FXR_PATCH_NAME) {
          return;
        }

        log('Found inline script for Vimeo player config');

        if (document.querySelector(`script[data-fxr-patch-for~="${FXR_PATCH_NAME}"]`)) {
          // Already injected inline <script> to patch this JS script and any loaded in the future.
          evt.target.setAttribute('data-fxr-patched', FXR_PATCH_NAME);
          return;
        }

        log(`Preventing execution of and removing Vimeo script`);

        // Stop the external JS script from being executed, and remove the <script> from the page.
        evt.preventDefault();
        evt.stopPropagation();
        target.remove();

        const scriptTextLines = scriptText.split('\n');
        let line = '';
        let idx = 0;
        for (; idx < scriptTextLines.length; idx++) {
          line = scriptTextLines[idx];
          if (VIMEO_SCRIPT_REGEXS.videoEmbedPage.test(line)) {
            line = line.trim().replace(/.*config\s*=\s*/i, '').replace(/;*$/, '');
            break;
          }
        }
        let configObj = null;
        try {
          configObj = JSON.parse(line);
        } catch (err) {
          logWarn('Could not parse line JSON from from JS variable assignment "config" for Vimeo player', err);
        }

        if (configObj) {
          log('Parsing "config" object');

          if (configObj.video) {
            if (configObj.video.spatial !== 0 || configObj.video.spatial.projection === 'equirectangular') {
              configObj.video.spatial = 0;
            }
            if (configObj.video.canvas !== 1) {
              configObj.video.canvas = 1;
            }
            if (configObj.embed.transparent !== 1) {
              configObj.embed.transparent = 1;
            }

            if (configObj.video.spatial === 0 || configObj.video.canvas === 1) {
              is360 = true;
            }

            if (is360) {
              const videoEmbedUrl = `https://${configObj.player_url}/video/${configObj.video.id}`;
              const newVideoUrl = getUrlWithNewQs(configObj.video.url);

              // Rewrite URLs to append `&canvas=1&mozVideoProjection=360_auto` query-string parameters.
              configObj.video.url = newVideoUrl.url;
              configObj.video.embed_code = videoEmbedUrl.replace(videoEmbedUrl, `${videoEmbedUrl}?${newVideoUrl.qs}`);
              configObj.video.share_url = getUrlWithNewQs(configObj.video.share_url);
            }
          }
        }

        log(is360 ? 'Detected a 360 video' : 'Did not detect a 360 video');
        updateCurrentQsInUrl();
        document.documentElement.setAttribute('data-fxr-is-360', is360 ? 'true' : 'false');
        document.documentElement.setAttribute('data-fxr-video-page-layout', 'detail');

        line = `var config = ${JSON.stringify(configObj)};`;
        scriptTextLines[idx] = line;

        const newScriptText = scriptTextLines.join('\n');
        // const newScriptText = `\nObject.defineProperty(navigator, 'userAgent', {get: () => '${CUSTOM_USER_AGENT}'});\n` +
        //   scriptTextLines.join('\n');

        // Inject the <script> back onto the page, but this time with the values of `config` changed.
        const scriptRedo = document.createElement('script');
        scriptRedo.textContent = newScriptText;
        scriptRedo.setAttribute('data-fxr-patched', FXR_PATCH_NAME);
        document.body.appendChild(scriptRedo);

        log('Re-injected inline script');

        window.removeEventListener('beforescriptexecute', patchBeforeScriptExecute);
      } catch (err) {
        log('Encountered error:', err);
      }
    }
  }
} catch (err) {
  console.error(LOGTAG, 'Encountered error:', err);
}

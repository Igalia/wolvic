(function () {
  // If missing, inject a `<meta name="viewport">` tag to trigger YouTube's mobile layout.
  window.addEventListener('load', () => {
    let viewport = document.head.querySelector('meta[name="viewport"]');
    if (!viewport) {
      viewport = document.createElement('meta');
      viewport.name = 'viewport';
      viewport.content = 'width=device-width, initial-scale=1';
      document.head.appendChild(viewport);
    }
  });

  const LOGTAG = '[firefoxreality:webcompat]'
  const qs = new URLSearchParams(window.location.search);
  let retryTimeout = null;

  function getTruthyQS (key) {
    if (!qs || !qs.has(key)) {
      return false;
    }
    const valueLower = (qs.get('key') || '').trim().toLowerCase();
    return valueLower === '' || valueLower === '1' || valueLower === 'true' || valueLower === 'yes' || valueLower === 'on';
  }

  const prefs = {
    hd: false,
    quality: 1440,
    log: qs.get('mozDebug') ? getTruthyQS('mozDebug') : true,
    retryAttempts: parseInt(qs.get('retryAttempts') || qs.get('retryattempts') || '10', 10),
    retryTimeout: parseInt(qs.get('retryTimeout') || qs.get('retrytimeout') || '500', 10)
  };

  const printLog = String(prefs.log) === 'true';

  const log = (...args) => printLog && console.log(LOGTAG, ...args);
  const logError = (...args) => printLog && console.error(LOGTAG, ...args);
  const logWarn = (...args) => printLog && console.warn(LOGTAG, ...args);

  const ytImprover = window.ytImprover = (state, attempts) => {
    if (ytImprover.completed) {
      return;
    }

    if (typeof attempts === 'undefined') {
      attempts = 1;
    }
    if (attempts >= prefs.retryAttempts) {
      logError(`Giving up trying to increase resolution after ${prefs.retryAttempts} attempts.`);
      return;
    }

    let player = document.getElementById('movie_player');
    let reason = 'unknown';
    if (state !== 1) {
      reason = 'invalid state';
    } else if (!player) {
      reason = 'player not found';
    } else if (!player.wrappedJSObject) {
      reason = 'player.wrappedJSObject not found';
      player = null;
    } else if (!player.wrappedJSObject.getAvailableQualityLevels) {
      reason = 'player.wrappedJSObject.getAvailableQualityLevels not found';
      player = null;
    }

    if (!player) {
      logWarn(`Cannot find player because ${reason}. attempts: ${attempts}`);
      attempts++;
      retryTimeout = setTimeout(() => {
        ytImprover(state, attempts);
      }, prefs.retryTimeout);
      return;
    }

    player = player.wrappedJSObject;

    const levels = player.getAvailableQualityLevels();
    if (!levels || !levels.length) {
      logWarn(`Cannot read 'player.getAvailableQualityLevels()' attempts: ${attempts}`);
      attempts++;
      retryTimeout = setTimeout(() => {
        ytImprover(state, attempts);
      }, prefs.retryTimeout);
      return;
    }

    clearTimeout(retryTimeout);
    ytImprover.completed = true;

    prefs.qualities = [
      'highres', 'h2880', 'hd2160', 'hd1440', 'hd1080', 'hd720', 'large', 'medium', 'small', 'tiny', 'auto'
    ];
    prefs.qualityLabels = {
      '4320': 'highres', // 8K / 4320p / QUHD
      '2880': 'hd2880', // 5K / 2880p / UHD+
      '2160': 'hd2160', // 4K / 2160p / UHD
      '1440': 'hd1440', // 1440p / QHD
      '1080': 'hd1080', // 1080p / FHD
      '720': 'hd720', // 720p / HD
      '480': 'large', // 480p
      '360': 'medium', // 360p
      '240': 'small', // 240p
      '144': 'tiny', // 144p
      '0': 'auto'
    };

    const getDesiredQuality = () => {
      const qsQuality = (qs.get('vq') || qs.get('quality') || '').trim().toLowerCase();
      if (qsQuality) {
        if (qsQuality in prefs.qualityLabels) {
          prefs.quality = prefs.qualityLabels[qsQuality];
        } else {
          const qsQualityNumber = parseInt(qsQuality, 10);
          if (Number.isInteger(qsQualityNumber)) {
            prefs.quality = qsQualityNumber;
          } else {
            prefs.quality = qsQuality;
          }
        }
      }
      prefs.quality = String(prefs.quality).toLowerCase();
      if (qsQuality === 'auto' || qsQuality === 'default') {
        prefs.quality = 'auto';
      }
      if (prefs.quality in prefs.qualityLabels) {
        prefs.quality = prefs.qualityLabels[prefs.quality];
      }
      return prefs.quality;
    };

    prefs.quality = getDesiredQuality();
    if (prefs.quality === 'auto') {
      return log(`Desired quality is fine (${prefs.quality})`);
    }

    const currentQuality = player.getPlaybackQuality();
    if (prefs.quality === currentQuality) {
      return log(`Current quality is desired quality (${currentQuality})`);
    }

    const findBestQuality = increase => {
      if (prefs.quality === 'highest' || prefs.quality === 'best' || prefs.quality === 'max' || prefs.quality === 'maximum') {
        return levels[0];
      }
      if (prefs.quality === 'lowest' || prefs.quality === 'worst' || prefs.quality === 'min' || prefs.quality === 'minimum') {
        return levels[levels.length - 1];
      }
      if (increase) {
        prefs.quality = prefs.qualities[prefs.qualities.indexOf(prefs.quality) - 1] || levels[0];
      }
      const index = levels.indexOf(prefs.quality);
      if (index !== -1) {
        return prefs.quality;
      }
      return findBestQuality(true);
    };
    const newBestQuality = findBestQuality();
    if (currentQuality === newBestQuality) {
      return log(`Current quality "${currentQuality}" is the best available quality`);
    }

    if (!player.setPlaybackQuality) {
      return logError('`player.setPlaybackQuality` not available');
    }
    player.setPlaybackQuality(newBestQuality);

    if (!player.setPlaybackQualityRange) {
      return logError('`player.setPlaybackQualityRange` not available');
    }
    try {
      player.setPlaybackQualityRange(newBestQuality, newBestQuality);
    } catch (e) {
      logError(`Failed to call 'player.setPlaybackQualityRange(${newBestQuality}, ${newBestQuality})' with exception: `, e);
      return;
    }

    log(`Changed quality from "${currentQuality}" to "${newBestQuality}"`);
  };

  if (window.location.pathname.startsWith('/watch')) {
    const onYouTubePlayerReady = window.onYouTubePlayerReady = evt => {
      log('`onYouTubePlayerReady` called');
      window.ytImprover(1);
      evt.addEventListener('onStateChange', 'ytImprover');
    };

    window.addEventListener('spfready', () => {
      log('`spfready` event fired');
      if (typeof window.ytplayer === 'object' && window.ytplayer.config) {
        log('`window.ytplayer.config.args.jsapicallback` set');
        window.ytplayer.config.args.jsapicallback = 'onYouTubePlayerReady';
      }
    });

    ytImprover(1);
  }
})();

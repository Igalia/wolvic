const LOGTAG = '[firefoxreality:webcompat:mediasession]';
const ENABLE_LOGS = true;
const logDebug = (...args) => ENABLE_LOGS && console.log(LOGTAG, ...args);
let video = null;


function updatePositionState(video) {
  if ('setPositionState' in navigator.mediaSession) {
    navigator.mediaSession.setPositionState({
      duration: video.duration,
      playbackRate: video.playbackRate,
      position: video.currentTime,
    });
  }
}

function handleVideoUpdate() {
    if (!video.duration) {
        return;
    }
    updatePositionState(video);
}

function mediaSessionFix() {
    video.addEventListener('ratechange', handleVideoUpdate);
    video.addEventListener('timeupdate', handleVideoUpdate);

    const defaultSkipTime = 10;

    // When user wants to seek backward, update position.
    navigator.mediaSession.setActionHandler('seekbackward', (details) => {
      const skipTime = details.seekOffset || defaultSkipTime;
      video.currentTime = Math.max(video.currentTime - skipTime, 0);
      updatePositionState(video);
    });

    // When user wants to seek forward, update position.
    navigator.mediaSession.setActionHandler('seekforward', (details) => {
      const skipTime = details.seekOffset || defaultSkipTime;
      video.currentTime = Math.min(video.currentTime + skipTime, video.duration);
      updatePositionState(video);
    });

    // When user wants to seek to a specific time, update position.
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (details.fastSeek && 'fastSeek' in video) {
        // Only use fast seek if supported.
        video.fastSeek(details.seekTime);
        return;
      }
      video.currentTime = details.seekTime;
      updatePositionState(video);
    });
}

document.addEventListener('fullscreenchange', (event) => {
  if (!document.fullscreenElement) {
    if (video) {
        video.removeEventListener('ratechange', handleVideoUpdate);
        video.removeEventListener('timeupdate', handleVideoUpdate);
    }
    video = null;
    return;
  }

  if (document.fullscreenElement.tagName.toLowerCase() === "video") {
    video = document.fullscreenElement;
  } else {
    video = document.fullscreenElement.getElementsByTagName("video")[0] || null;
  }

  if (video) {
    logDebug("Apply MediaSession fixes for video");
    mediaSessionFix();
  }
});
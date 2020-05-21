const forceRedraw = function(element) {
  const disp = element.style.display
  element.style.display = 'none'
  // This doesn't *seem* to do anything though apparently it does
  const trick = element.offsetHeight
  element.style.display = disp
}

const fpsEl = document.querySelector('#fps')
const root = document.querySelector('#root')

const fpsCounter = () => {
  const frames = []
  return () => {
    const now = new Date()
    frames.push(+now)
    const before = now.setSeconds(now.getSeconds() - 1)
    const beforeMs = +before
    while (frames.length && frames[0] < beforeMs) {
      frames.shift()
    }
    return frames.length
  }
}

const getFps = fpsCounter()

const forceRedraws = () => {
  const fps = getFps()
  fpsEl.innerHTML = String(fps)
  forceRedraw(root)
  requestAnimationFrame(forceRedraws)
}

try {
  chrome.runtime.getPlatformInfo(function(result) {
    if (result.os === 'mac') {
      forceRedraws()
    }
  })
} catch (e) {
  console.warn('Could not getPlatformInfo', e)
}

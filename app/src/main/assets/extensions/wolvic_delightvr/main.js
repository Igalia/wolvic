const LOGTAG = '[firefoxreality:webcompat:delightvr]';

try {
  const utils = {
    escapeChars: {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }
  };
  utils.getEscapedStr = str => {
    if (!utils._escapeStrEl) {
      utils._escapeStrEl = document.createElement('span');
    }
    str = str.replace(/[&<>'"]/g, char => utils.escapeChars[char]);
    utils._escapeStrEl.textContent = str;
    return utils._escapeStrEl.textContent;
  };
  utils._qs = new URLSearchParams(window.location.search);
  utils.GET = {};
  for (let [qsKey, qsValue] of utils._qs) {
    utils.GET[qsKey.toLowerCase()] = qsValue;
  }

  const prefs = {
    log: utils.GET.debug !== '0' && utils.GET.mozdebug !== '0',
    customUserAgent: utils.GET.mozuseragent,
    active: utils.GET.mozfxrpatch !== '0'
  };

  // Note: By appending such a value as " BlackBerry" to `navigator.userAgent`, we trick the Delight-VR
  // player's JavaScript code into detecting FxR as a phone instead of incorrectly as an "UnknownTablet".
  // See https://github.com/MozillaReality/FirefoxReality/issues/1212 for context.
  if (prefs.active) {
    prefs.customUserAgent = utils.getEscapedStr(prefs.customUserAgent || `${navigator.userAgent} BlackBerry`);
  }

  const log = (...args) => prefs.log && console.log(LOGTAG, ...args);

  if (prefs.customUserAgent) {
    window.addEventListener('beforescriptexecute', patchBeforeScriptExecute);
    // Note: See docs on `beforescriptexecute`, a Gecko-proprietary event:
    // - https://bugzilla.mozilla.org/show_bug.cgi?id=587931
    // - https://developer.mozilla.org/en-US/docs/Web/API/Element/beforescriptexecute_event
    // - https://github.com/whatwg/html/commit/69f83cf
    // - https://github.com/whatwg/html/issues/943
    // - https://github.com/whatwg/html/pull/1103
  }

  function patchBeforeScriptExecute (evt) {
    const target = evt.target;

    const src = target && target.src;
    // Ignore any scripts that do not look like Delight-VR's JS player.
    if (!src || (!src.includes('dl8-') && !src.includes('.delight-')) ||
        (!target || target.getAttribute('data-fxr-patched') === 'ua')) {
      return;
    }

    log(`Found Delight-VR Player script: "${src}"`);

    if (document.head.querySelector('script[data-fxr-patch-for~="ua"]')) {
      // Already injected inline <script> to patch this JS script and any loaded in the future.
      evt.target.setAttribute('data-fxr-ua-patched', true);
      return;
    }

    // Stop the external JS script from being executed, and remove the <script> from the page.
    evt.preventDefault();
    evt.stopPropagation();
    target.remove();

    // Inject an inline <script> onto the page to override the value of
    // `navigator.userAgent` when acccessed by future scripts.
    const scriptPatch = document.createElement('script');
    scriptPatch.setAttribute('data-fxr-patch-for', 'ua');
    scriptPatch.setAttribute('data-fxr-patched', 'ua');
    scriptPatch.textContent = `
  // *** Injected by Firefox Reality ***
  console.log('${LOGTAG}', 'Setting custom UA: "${prefs.customUserAgent}"');
  window.mozFxr = window.mozFxr || {};
  window.mozFxr.patched = Object.assign(window.mozFxr.patched || {}, {ua: {before: navigator.userAgent}});
  if (navigator.userAgent !== '${prefs.customUserAgent}') {
    Object.defineProperty(navigator, 'userAgent', {get: () => '${prefs.customUserAgent}'});
  }
  window.mozFxr.patched.ua.after = navigator.userAgent;
    `.trim();
    document.head.appendChild(scriptPatch);

    // Inject the <script src="…"> back onto the page, but this time let the
    // script run so it can use the now-patched `navigator.userAgent` value.
    const scriptRedo = document.createElement('script');
    scriptRedo.src = src;
    scriptRedo.async = true;
    scriptRedo.setAttribute('data-fxr-patched', 'ua');
    document.head.appendChild(scriptRedo);

    window.removeEventListener('beforescriptexecute', patchBeforeScriptExecute);
  }
} catch (err) {
  console.error(LOGTAG, 'Encountered error:', err);
}

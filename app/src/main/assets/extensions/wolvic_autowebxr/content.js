const LOGTAG = '[wolvic:autowebxr]';
const ENABLE_LOGS = true;
const logDebug = (...args) => ENABLE_LOGS && console.log(LOGTAG, ...args);

const PARENT_ELEMENT_XPATH_PARAMETER = 'wolvic-autowebxr-parentElementXPath';
const TARGET_ELEMENT_XPATH_PARAMETER = 'wolvic-autowebxr-targetElementXPath';

var parentElementXPath;
var targetElementXPath;

function getElementByXPath(document, xpath) {
    let result = document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
    return result.singleNodeValue;
}

function clickImmersiveElement() {
    // check if the current URL has extra query parameters
    parentElementXPath = undefined;
    targetElementXPath = undefined;

    let url = document.URL;
    let params = new URLSearchParams(new URL(url).search);
    for (let [key, value] of params) {
        if (key === 'wolvic-autowebxr-parentElementXPath')
            parentElementXPath = value
        else if (key === 'wolvic-autowebxr-targetElementXPath')
            targetElementXPath = value;
    }

    // we need at least the target element to click
    if (!targetElementXPath)
        return;

    logDebug('Preparing to open immersive WebXR; parentElementXPath: ' + parentElementXPath + ' ; targetElementXPath: ' + targetElementXPath);

    // the parent element is usually an iframe; if parentElementXPath is null, we use the root

    var parent, parentDocument;
    if (parentElementXPath) {
        var parent = getElementByXPath(document, parentElementXPath);
        if (!parent) {
            logDebug('parent not found, retrying');
            setTimeout(clickImmersiveElement, 1000);
            return;
        }
        parentDocument = parent.contentDocument || parent.contentWindow.document;
    } else {
        parent = window;
        parentDocument = document;
    }

    if (parentDocument.readyState !== 'complete') {
        logDebug('parent is still loading');
        parent.addEventListener('load', function() {
            logDebug('parent has finished loading');
            clickImmersiveElement();
        });
        return;
    }

    logDebug('parent is loaded');

    let targetElement = getElementByXPath(parentDocument, targetElementXPath);

    if (targetElement) {
        logDebug('target element found, clicking');
        // This fails with "DOMException: A user gesture is required" unless dom.vr.require-gesture is set to false.
        targetElement.click();
    } else {
        logDebug('target element not found, retrying');
        setTimeout(clickImmersiveElement, 1000);
    }
}

if (document.readyState === 'complete') {
    logDebug('document is completely ready');
    clickImmersiveElement();
} else {
    logDebug('document is not ready yet');
    window.addEventListener('load', clickImmersiveElement);
}

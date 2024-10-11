const LOGTAG = '[wolvic:launchimmersive]';
const ENABLE_LOGS = true;
const logDebug = (...args) => ENABLE_LOGS && console.log(LOGTAG, ...args);

const PARENT_ELEMENT_XPATH_PARAMETER = 'wolvic-launchimmersive-parentElementXPath';
const TARGET_ELEMENT_XPATH_PARAMETER = 'wolvic-launchimmersive-targetElementXPath';

const IFRAME_READY_MSG = 'wolvic-launchimmersive-iframeReady';
const TARGET_ELEMENT_MSG = 'wolvic-launchimmersive-targetElement';

var parentElementXPath;
var targetElementXPath;

function getElementByXPath(document, xpath) {
    let result = document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
    return result.singleNodeValue;
}

// Limit the number of times that we can try to launch the experience, to avoid an infinite loop.
var retryCounter = 0;
const RETRY_LIMIT = 20;
function retryAfterTimeout(code, delay) {
    if (retryCounter < RETRY_LIMIT) {
        retryCounter++;
        setTimeout(code, delay);
    } else {
        logDebug('Retry limit reached, will not try again');
    }
}

function clickImmersiveElement() {
    // Check if the current URL has extra query parameters
    parentElementXPath = undefined;
    targetElementXPath = undefined;

    let url = document.URL;
    let params = new URLSearchParams(new URL(url).search);
    for (let [key, value] of params) {
        if (key === PARENT_ELEMENT_XPATH_PARAMETER)
            parentElementXPath = value;
        else if (key === TARGET_ELEMENT_XPATH_PARAMETER)
            targetElementXPath = value;
    }

    // We need at least the target element to click
    if (!targetElementXPath)
        return;

    logDebug('Preparing to open immersive WebXR; parentElementXPath: ' + parentElementXPath + ' ; targetElementXPath: ' + targetElementXPath);

    // The parent element is typically an iframe and, if it comes from a different origin,
    // we might not be able to access its contents directly.
    // If parentElementXPath is null, we will use the root to find the target element.

    var parent, parentDocument;
    if (parentElementXPath) {
        parent = getElementByXPath(document, parentElementXPath);
        if (!parent) {
            logDebug('Parent element not found, retrying');
            retryAfterTimeout(clickImmersiveElement, 1000);
            return;
        }

        try {
            parentDocument = parent.contentDocument || parent.contentWindow.document;
        } catch (e) {
            logDebug('Parent iframe is from a different origin');
            const iframeWindow = parent.contentWindow;

            const targetElementMsg = {
                action: TARGET_ELEMENT_MSG,
                targetElementXPath: targetElementXPath
            };

            iframeWindow.postMessage(targetElementMsg, '*');

            // The iframe might not be ready yet, so we set up a listener for the "iframe ready" message.
            const handleIframeReady = (event) => {
                if (event.source === iframeWindow && event.data === IFRAME_READY_MSG) {
                    window.removeEventListener('message', handleIframeReady);
                    iframeWindow.postMessage(targetElementMsg, '*');
                }
            };
            window.addEventListener('message', handleIframeReady);

            return;
        }
    } else {
        parent = window;
        parentDocument = document;
    }

    if (parentDocument.readyState !== 'complete') {
        logDebug('Parent is still loading');
        parent.addEventListener('load', function() {
            logDebug('Parent has finished loading');
            clickImmersiveElement();
        });
        return;
    } else {
        logDebug('Parent is loaded');
    }

    let targetElement = getElementByXPath(parentDocument, targetElementXPath);

    if (targetElement) {
        logDebug('Target element found, calling click()');
        targetElement.click();
    } else {
        logDebug('Target element not found, we will try again');
        retryAfterTimeout(clickImmersiveElement, 1000);
    }
}

function launchImmersiveFromIframe() {
    window.addEventListener('message', function(event) {
        if (event.data.action === TARGET_ELEMENT_MSG) {
            let targetElement = getElementByXPath(document, event.data.targetElementXPath);

            if (targetElement) {
                logDebug('Target element found in iframe, calling click()');
                targetElement.click();
            } else {
                logDebug('Target element not found in iframe, retrying');
                retryAfterTimeout(function() {
                    window.postMessage(event.data, '*');
                }, 1000);
            }
        }
    });

    window.parent.postMessage(IFRAME_READY_MSG, '*');
}

// Main script execution
if (window.top === window.self) {
    if (document.readyState === 'complete') {
        logDebug('Root document is completely ready');
        clickImmersiveElement();
    } else {
        logDebug('Root document is not ready yet');
        window.addEventListener('load', clickImmersiveElement);
    }
} else {
    if (document.readyState === 'complete') {
        logDebug('Iframe is completely ready');
        launchImmersiveFromIframe();
    } else {
        logDebug('Iframe is not ready yet');
        window.addEventListener('load', launchImmersiveFromIframe);
    }
}

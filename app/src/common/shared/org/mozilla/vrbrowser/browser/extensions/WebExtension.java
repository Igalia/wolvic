package org.mozilla.vrbrowser.browser.extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.mozilla.vrbrowser.browser.engine.Session;

public abstract class WebExtension {

    protected String mId;
    protected String mUrl;

    /**
     * Represents a browser extension based on the WebExtension API:
     * https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions
     *
     * @param id the unique ID of this extension.
     * @param url the url pointing to a resources path for locating the extension
     * within the APK file e.g. resource://android/assets/extensions/my_web_ext.
     */
    WebExtension(@NonNull String id, @NonNull String url) {
        mId = id;
        mUrl = url;
    }

    /**
     * Registers a [MessageHandler] for message events from background scripts.
     *
     * @param name the name of the native "application". This can either be the
     * name of an application, web extension or a specific feature in case
     * the web extension opens multiple [Port]s. There can only be one handler
     * with this name per extension and the same name has to be used in
     * JavaScript when calling `browser.runtime.connectNative` or
     * `browser.runtime.sendNativeMessage`. Note that name must match
     * /^\w+(\.\w+)*$/).
     * @param messageHandler the message handler to be notified of messaging
     * events e.g. a port was connected or a message received.
     */
    public abstract void registerBackgroundMessageHandler(@NonNull String name, @NonNull MessageHandler messageHandler);

    /**
     * Registers a [MessageHandler] for message events from content scripts.
     *
     * @param session the session to be observed / attach the message handler to.
     * @param name the name of the native "application". This can either be the
     * name of an application, web extension or a specific feature in case
     * the web extension opens multiple [Port]s. There can only be one handler
     * with this name per extension and session, and the same name has to be
     * used in JavaScript when calling `browser.runtime.connectNative` or
     * `browser.runtime.sendNativeMessage`. Note that name must match
     * /^\w+(\.\w+)*$/).
     * @param messageHandler the message handler to be notified of messaging
     * events e.g. a port was connected or a message received.
     */
    public abstract void registerContentMessageHandler(@NonNull Session session, @NonNull String name, @NonNull MessageHandler messageHandler);

    /**
     * Checks whether there is an existing content message handler for the provided
     * session and "application" name.
     *
     * @param session the session the message handler was registered for.
     * @param name the "application" name the message handler was registered for.
     * @return true if a content message handler is active, otherwise false.
     */
    public abstract boolean hasContentMessageHandler(@NonNull Session session, @NonNull String name);

    /**
     * Returns a connected port with the given name and for the provided
     * [EngineSession], if one exists.
     *
     * @param name the name as provided to connectNative.
     * @param session (optional) session to check for, null if port is from a
     * background script.
     * @return a matching port, or null if none is connected.
     */
    @Nullable
    public abstract Port getConnectedPort(@NonNull String name, @Nullable Session session);

    /**
     * Disconnect a [Port] of the provided [EngineSession]. This method has
     * no effect if there's no connected port with the given name.
     *
     * @param name the name as provided to connectNative, see
     * [registerContentMessageHandler] and [registerBackgroundMessageHandler].
     * @param session (options) session for which ports should disconnected,
     * null if port is from a background script.
     */
    public abstract void disconnectPort(@NonNull String name, @Nullable Session session);

    /**
     * A handler for all messaging related events, usable for both content and
     * background scripts.
     *
     * [Port]s are exposed to consumers (higher level components) because
     * how ports are used, how many there are and how messages map to it
     * is feature-specific and depends on the design of the web extension.
     * Therefore it makes most sense to let the extensions (higher-level
     * features) deal with the management of ports.
     */
    public interface MessageHandler {

        /**
         * Invoked when a [Port] was connected as a result of a
         * `browser.runtime.connectNative` call in JavaScript.
         *
         * @param port the connected port.
         */
        void  onPortConnected(@NonNull Port port);

        /**
         * Invoked when a [Port] was disconnected or the corresponding session was
         * destroyed.
         *
         * @param port the disconnected port.
         */
        void onPortDisconnected(@NonNull Port port);

        /**
         * Invoked when a message was received on the provided port.
         *
         * @param message the received message, either be a primitive type
         * or a org.json.JSONObject.
         * @param port the port the message was received on.
         */
        void onPortMessage(@NonNull Object message, @NonNull Port port);

        /**
         * Invoked when a message was received as a result of a
         * `browser.runtime.sendNativeMessage` call in JavaScript.
         *
         * @param message the received message, either be a primitive type
         * or a org.json.JSONObject.
         * @param source the session this message originated from if from a content
         * script, otherwise null.
         * @return the response to be sent for this message, either a primitive
         * type or a org.json.JSONObject, null if no response should be sent.
         */
        @Nullable
        Object onMessage(@NonNull Object message, @Nullable Session source);
    }

    /**
     * Represents a port for exchanging messages:
     * https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/runtime/Port
     */
    abstract class Port {

        Session mEngineSession;

        public Port(@Nullable Session session) {
            mEngineSession = session;
        }

        /**
         * Sends a message to this port.
         *
         * @param message the message to send.
         */
        abstract void postMessage(@NonNull JSONObject message);

        /**
         * Returns the name of this port.
         */
        abstract String name();

        /**
         * Disconnects this port.
         */
        abstract void disconnect();
    }

}

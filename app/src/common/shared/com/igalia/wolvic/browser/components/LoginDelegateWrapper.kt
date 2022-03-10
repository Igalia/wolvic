/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.browser.components

import com.igalia.wolvic.browser.api.WAutocomplete
import mozilla.components.concept.storage.Login

class LoginDelegateWrapper {
    companion object {
        /**
         * Converts a ISession [LoginStorage.LoginEntry] to an Android Components [Login]
         */
        @JvmStatic
        fun WAutocomplete.LoginEntry.toLogin() = Login(
                guid = guid,
                origin = origin.orEmpty(),
                formActionOrigin = formActionOrigin,
                httpRealm = httpRealm,
                username = username,
                password = password
        )

        /**
         * Converts an Android Components [Login] to a ISession [LoginStorage.LoginEntry]
         */
        @JvmStatic
        fun Login.toLoginEntry() = WAutocomplete.LoginEntry.Builder()
                .guid(guid)
                .origin(origin)
                .formActionOrigin(formActionOrigin)
                .httpRealm(httpRealm)
                .username(username)
                .password(password)
                .build()
    }
}

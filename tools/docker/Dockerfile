# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

# Inspired by:
# https://hub.docker.com/r/runmymind/docker-android-sdk/~/dockerfile/

FROM ubuntu:18.04

MAINTAINER Randall Barker "rbarker@mozilla.com"

# -- System -----------------------------------------------------------------------------

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update -qq

RUN apt-get install -y openjdk-8-jdk \
					   wget \
					   expect \
					   git \
					   curl \
					   ruby \
					   ruby-dev \
					   ruby-build \
					   python \
					   python-pip \
					   optipng \
					   imagemagick \
					   locales
RUN gem install fastlane
RUN pip install taskcluster

RUN locale-gen en_US.UTF-8

# -- Android SDK ------------------------------------------------------------------------

RUN cd /opt && wget -q https://dl.google.com/android/repository/commandlinetools-linux-6200805_latest.zip -O android-sdk.zip
RUN cd /opt && unzip android-sdk.zip
RUN cd /opt && rm -f android-sdk.zip

ENV ANDROID_BUILD_TOOLS_VERSION_29 "29.0.2"
ENV ANDROID_SDK_HOME /opt
ENV ANDROID_HOME /opt
ENV PATH ${PATH}:${ANDROID_SDK_HOME}/tools/bin:${ANDROID_SDK_HOME}/platform-tools:/opt/tools:${ANDROID_SDK_HOME}/build-tools/${ANDROID_BUILD_TOOLS_VERSION_29}

RUN echo y | sdkmanager "build-tools;${ANDROID_BUILD_TOOLS_VERSION_29}" --sdk_root=$ANDROID_SDK_HOME
RUN echo y | sdkmanager "ndk-bundle" --sdk_root=$ANDROID_SDK_HOME
RUN echo y | sdkmanager "cmake;3.10.2.4988404" --sdk_root=$ANDROID_SDK_HOME
RUN echo y | sdkmanager "platforms;android-29" --sdk_root=$ANDROID_SDK_HOME

WORKDIR /opt

# Checkout source code
RUN git clone https://github.com/MozillaReality/FirefoxReality.git

# Build project and run gradle tasks once to pull all dependencies
WORKDIR /opt/FirefoxReality
RUN git config --global user.email "noreply@mozilla.com"
RUN git config --global user.name "No Reply"
RUN echo sdk.dir=/opt > local.properties && echo ndk.dir=/opt/ndk-bundle >> local.properties
RUN git checkout -b update origin/gradle
RUN git rebase origin/main
RUN git submodule init
RUN git submodule update
RUN ./gradlew --no-daemon assembleNoapi
RUN ./gradlew --no-daemon clean
RUN git checkout main

# -- Cleanup ----------------------------------------------------------------------------

RUN apt-get clean

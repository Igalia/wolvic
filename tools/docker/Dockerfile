# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

# Inspired by:
# https://hub.docker.com/r/runmymind/docker-android-sdk/~/dockerfile/

FROM ubuntu:17.10

MAINTAINER Randall Barker "rbarker@mozilla.com"

# -- System -----------------------------------------------------------------------------

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

RUN cd /opt && wget -q https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip -O android-sdk.zip
RUN cd /opt && unzip android-sdk.zip
RUN cd /opt && rm -f android-sdk.zip

ENV ANDROID_SDK_HOME /opt
ENV ANDROID_HOME /opt
ENV PATH ${PATH}:${ANDROID_SDK_HOME}/tools/bin:${ANDROID_SDK_HOME}/platform-tools:/opt/tools:${ANDROID_SDK_HOME}/build-tools/27.0.3

RUN echo y | sdkmanager "build-tools;26.0.0"
RUN echo y | sdkmanager "build-tools;27.0.3"
RUN echo y | sdkmanager "ndk-bundle"
RUN echo y | sdkmanager "cmake;3.6.4111459"
RUN echo y | sdkmanager "platforms;android-26"
RUN echo y | sdkmanager "platforms;android-27"

WORKDIR /opt

# Checkout source code
RUN git clone https://github.com/MozillaReality/FirefoxReality.git

# Build project and run gradle tasks once to pull all dependencies
WORKDIR /opt/FirefoxReality
RUN git submodule init
RUN git submodule update
RUN cd gvr-android-sdk && ./gradlew --no-daemon extractNDK
RUN ./gradlew --no-daemon assembleGooglevr assembleNoapi
RUN ./gradlew --no-daemon clean

# -- Cleanup ----------------------------------------------------------------------------

RUN apt-get clean

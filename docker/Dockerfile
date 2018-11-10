#
# Build this image with the command
#   docker build -f docker/build -t dyne/social-wallet-api:latest
#
# Then run with the command
#   docker run -p 3000:3000 -it dyne/social-wallet-api:latest
#

FROM dyne/clojure
LABEL maintainer="Denis Roio <jaromil@dyne.org>" \
	  homepage="https://github.com/commonfare-net/social-wallet-api"
# ENV VERSION=AUTO_STRICT

ENV LEIN_VERSION=2.8.1 \
	LEIN_INSTALL=/usr/local/bin/ \
	PATH=$PATH:$LEIN_INSTALL \
	LEIN_ROOT=1


## needed for update-alternatives to not fail
RUN apt-get update \
	&& apt-get install -y -q --allow-downgrades --no-install-recommends \
       		   mongodb \
 	&& apt-get clean \
	&& rm -rf /var/lib/apt/lists/*

EXPOSE 3000 19999

ENV SWAPI_HOST=0.0.0.0
WORKDIR /app
COPY . /app
RUN lein deps
CMD /etc/init.d/mongodb start \
	&& lein ring server-headless

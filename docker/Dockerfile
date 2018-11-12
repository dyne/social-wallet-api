#
# Build this image with the command
#   docker build -f docker/build -t dyne/social-wallet-api:latest
#
# Then run with the command
#   docker run -p 3000:3000 -it dyne/social-wallet-api:latest
#

FROM dyne/clojure
ENV debian buster

LABEL maintainer="Denis Roio <jaromil@dyne.org>" \
	  homepage="https://github.com/commonfare-net/social-wallet-api"
# ENV VERSION=AUTO_STRICT

ENV LEIN_VERSION=2.8.1 \
	LEIN_INSTALL=/usr/local/bin/ \
	PATH=$PATH:$LEIN_INSTALL \
	LEIN_ROOT=1


# Nodejs repository
ADD https://deb.nodesource.com/gpgkey/nodesource.gpg.key nodesource.gpg.key
RUN apt-key add nodesource.gpg.key
RUN	echo "deb https://deb.nodesource.com/node_8.x $debian main" \
	>> /etc/apt/sources.list


## needed for update-alternatives to not fail
RUN apt-get update \
	&& apt-get install -y -q --allow-downgrades --no-install-recommends \
       		   mongodb nodejs supervisor gnupg1 ca-certificates\
 	&& apt-get clean \
	&& rm -rf /var/lib/apt/lists/*

# fix npm - not the latest version installed by apt-get
RUN npm install -g npm
RUN npm install -g mongo-express


ADD https://raw.githubusercontent.com/Commonfare-net/social-wallet-api/docker/docker/supervisord.conf \
	/etc/supervisor/supervisord.conf
RUN sed -i "s/nodaemon=true/nodaemon=$foreground/" /etc/supervisor/supervisord.conf

# supervisor
EXPOSE 9001 9001
# SWAPI
EXPOSE 3000
# mongo-express
EXPOSE 8081

WORKDIR /app
COPY . /app

# cache build deps
RUN lein deps

CMD bash -c '/etc/init.d/supervisor start'

FROM eclipse-temurin:25-noble

ENV DEBIAN_FRONTEND=noninteractive \
    TZ=Asia/Tokyo

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        git \
        vim \
        curl \
        sudo \
    && rm -rf /var/lib/apt/lists/*

ARG USERNAME=user
RUN userdel -r ubuntu 2>/dev/null; \
    useradd -m -s /bin/bash -u 1000 ${USERNAME} && \
    echo "${USERNAME} ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers && \
    mkdir -p /workspace && \
    chown ${USERNAME}:${USERNAME} /workspace

USER ${USERNAME}
RUN mkdir -p /home/${USERNAME}/.gradle
WORKDIR /workspace

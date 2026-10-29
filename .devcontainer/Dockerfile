FROM debian:latest

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai

# ---------- 系统依赖和上海时区 ----------
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    wget \
    git \
    unzip \
    zip \
    xz-utils \
    tar \
    bash \
    procps \
    iproute2 \
    openssh-client \
    build-essential \
    zlib1g-dev \
    libc6-dev \
    gcc \
    g++ \
    make \
    perl \
    locales \
    tzdata \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && dpkg-reconfigure -f noninteractive tzdata \
    && rm -rf /var/lib/apt/lists/*
# ---------- 安装 SDKMAN! ----------
ENV SDKMAN_DIR=/opt/sdkman

RUN curl -fsSL "https://get.sdkman.io" | bash

# ---------- 安装 GraalVM Java 25 和 Gradle ----------
# 使用 SDKMAN! 的 graal 发行版标识安装 Oracle GraalVM
# 通过 sdk list java 确认当前可用的 25.x-graal 版本
RUN bash -lc '\
    source "${SDKMAN_DIR}/bin/sdkman-init.sh" && \
    sdk install java 25.0.2-graal && \
    sdk default java 25.0.2-graal && \
    sdk install gradle \
'

# ---------- 安装 NVM ----------
ENV NVM_DIR=/opt/nvm

RUN mkdir -p "${NVM_DIR}" && \
    curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh \
    | PROFILE=/dev/null bash
# ---------- 配置所有 Bash 会话 ----------
RUN cat > /etc/profile.d/dev-env.sh <<'EOF'
export TZ=Asia/Shanghai
export SDKMAN_DIR=/opt/sdkman
export NVM_DIR=/opt/nvm

if [ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    source "$SDKMAN_DIR/bin/sdkman-init.sh"
fi

if [ -s "$NVM_DIR/nvm.sh" ]; then
    source "$NVM_DIR/nvm.sh"
fi
EOF

# ---------- 安装 Node.js ----------
# 默认安装 Node.js 22；需要其他版本可修改此处
RUN bash -lc '\
    source /etc/profile.d/dev-env.sh && \
    nvm install 22 && \
    nvm alias default 22 && \
    nvm use default && \
    node --version && \
    npm --version \
'

# ---------- 固定 Java 环境变量 ----------
ENV JAVA_HOME=/opt/sdkman/candidates/java/current
ENV GRADLE_HOME=/opt/sdkman/candidates/gradle/current
ENV PATH="${JAVA_HOME}/bin:${GRADLE_HOME}/bin:/opt/nvm/versions/node/v22.*/bin:${PATH}"
WORKDIR /workspace

# ---------- 构建时检查 ----------
RUN bash -lc '\
    source /etc/profile.d/dev-env.sh && \
    java -version && \
    javac -version && \
    native-image --version && \
    gradle --version && \
    node --version && \
    npm --version \
'

CMD ["/bin/bash"]

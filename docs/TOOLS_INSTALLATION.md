# Tools Installation

## asdf

```sh
$ brew install asdf
$ echo -e "\n. $(brew --prefix asdf)/libexec/asdf.sh" >> ${ZDOTDIR:-~}/.zshrc
```

https://asdf-vm.com/

For Ubuntu, install according to the following.

https://asdf-vm.com/guide/getting-started.html

## jq

```shell
$ asdf plugin-add jq https://github.com/AZMCode/asdf-jq.git
$ asdf install jq 1.6
$ asdf local jq 1.6
$ jq --version
jq-1.6
```

https://github.com/ryodocx/asdf-jq

### Java

```shell
$ asdf plugin-add java https://github.com/halcyon/asdf-java.git
```

https://github.com/halcyon/asdf-java

```shell
$ asdf install java temurin-17.0.4+101
$ asdf local java temurin-17.0.4+101
```

### Docker

#### Mac

Follow the instructions below to install it.

Docker Desktop for Mac

https://docs.docker.com/desktop/install/mac-install/

#### Ubuntu

Follow the instructions below to install it.

- Docker Desktop for Ubuntu
    - https://docs.docker.com/desktop/install/debian/
- Docker Engine for Ubuntu
    - https://docs.docker.com/engine/install/ubuntu/

In addition, to be able to use it without sudo, please follow these steps.

https://qiita.com/katoyu_try1/items/1bdaaad9f64af86bbfb7

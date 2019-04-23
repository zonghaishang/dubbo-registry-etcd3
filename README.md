# dubbo-registry-etcd3
etcd3 registry for dubbo framework

The code has been merged into incubator-dubbo([dubbo-registry](https://github.com/apache/incubator-dubbo/tree/master/dubbo-registry)).

## Easy to use etcd3

Add dependencies in `pom.xml`:
```xml
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-registry-etcd3</artifactId>
    <version>2.7.0-SNAPSHOT</version>
</dependency>
```

Stand-alone mode：
```xml
<dubbo:registry address="etcd3://127.0.0.1:2379"/>
```

Cluster mode:
```xml
<dubbo:registry address="ip:port,ip:port,..." protocol="etcd3"/>
```

## Etcd3 registry configuration

The startup `etcd3` explained here is mainly for local operation to track internal details, and the cluster startup is also a pseudo cluster started locally.

Need to specify the environment variable, add in the mac "" `~/.bash_profile`:

```shell
# etcd
export ETCDCTL_API=3
```

If you use zsh's shell, add `~/.bash_profile` to `~/.zshrc`:

```shell
source /Users/yourUserName/.bash_profile
```

## Install etcd

Go to the [etcd installation package](https://github.com/etcd-io/etcd/releases) and select the installation package for the corresponding platform. Take mac as an example:
``` shell
1. Unzip and copy etcd and etcdctl to the `/usr/local/bin` directory
2. Make sure `/usr/local/bin` exists in the path environment variable

If it doesn't exist, put the following in `~/.bash_profile`

PATH="${PATH}:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Users/yourUserName/go/bin"
export PATH

```


## Stand-alone startup etcd

Because the variables are configured, they are executed directly at the terminal:

```
~ etcd
```

After started, it will listen to port 2379 and output the log:

```
2018-03-02 10:44:48.864928 N | embed: serving insecure client requests on 127.0.0.1:2379, this is strongly discouraged!
```

## Cluster startup etcd

1. Install [goreman](https://github.com/mattn/goreman)

```shell
go get github.com/mattn/goreman
```

2. Add environment variables

``` shell
Put the following in `~/.bash_profile`
PATH="${PATH}:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Users/yourUserName/go/bin"
export PATH
```

Step 1 Install goreman, it will generate `/Users/yourUserName/go/bin`, which contains goreman.


3. Create a Procfile to the user directory `~`

Copy the following command to Procfile：
````
# Use goreman to run `go get github.com/mattn/goreman`
etcd1: etcd --name infra1 --listen-client-urls http://127.0.0.1:2379 --advertise-client-urls http://127.0.0.1:2379 --listen-peer-urls http://127.0.0.1:12380 --initial-advertise-peer-urls http://127.0.0.1:12380 --initial-cluster-token etcd-cluster-1 --initial-cluster 'infra1=http://127.0.0.1:12380,infra2=http://127.0.0.1:22380,infra3=http://127.0.0.1:32380' --initial-cluster-state new --enable-pprof
etcd2: etcd --name infra2 --listen-client-urls http://127.0.0.1:22379 --advertise-client-urls http://127.0.0.1:22379 --listen-peer-urls http://127.0.0.1:22380 --initial-advertise-peer-urls http://127.0.0.1:22380 --initial-cluster-token etcd-cluster-1 --initial-cluster 'infra1=http://127.0.0.1:12380,infra2=http://127.0.0.1:22380,infra3=http://127.0.0.1:32380' --initial-cluster-state new --enable-pprof
etcd3: etcd --name infra3 --listen-client-urls http://127.0.0.1:32379 --advertise-client-urls http://127.0.0.1:32379 --listen-peer-urls http://127.0.0.1:32380 --initial-advertise-peer-urls http://127.0.0.1:32380 --initial-cluster-token etcd-cluster-1 --initial-cluster 'infra1=http://127.0.0.1:12380,infra2=http://127.0.0.1:22380,infra3=http://127.0.0.1:32380' --initial-cluster-state new --enable-pprof
#proxy: bin/etcd grpc-proxy start --endpoints=127.0.0.1:2379,127.0.0.1:22379,127.0.0.1:32379 --listen-addr=127.0.0.1:23790 --advertise-client-url=127.0.0.1:23790 --enable-pprof
````

4. start cluster

```shell
 ~ goreman start
```

After startup, it will listen ports on 2379, 22379, 32379 and output the log:

```
10:57:26 etcd1 | 2018-03-02 10:57:26.143536 N | embed: serving insecure client requests on 127.0.0.1:2379, this is strongly discouraged!
10:57:26 etcd2 | 2018-03-02 10:57:26.152223 N | embed: serving insecure client requests on 127.0.0.1:22379, this is strongly discouraged!
10:57:26 etcd3 | 2018-03-02 10:57:26.143620 N | embed: serving insecure client requests on 127.0.0.1:32379, this is strongly discouraged!
```

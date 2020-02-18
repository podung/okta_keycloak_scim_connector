# Offline Keycloak Auth Documentation

## Stuff you need:

1) OPP Agent Container (see below)
  * configure your local agent - and see it running here: https://{okta org url}-admin.okta.com/admin/agents
  * after configured, go to your SWA App, go to provisioining tab, and select your agent
  * go to push groups tab and try to push your groups
2) Tomcat running locally on your dev box
3) openjdk (11)
4) keycloak: https://hub.docker.com/r/jboss/keycloak/ (`docker run -p 9090:8080 jboss/keycloak` - NOTE 9090 port)
5) example-server code from this repo, built, and dropped into tomcat

## Running OPP Agent

### Run container and install oktaonprem inside container
```
# https://hub.docker.com/r/oktaadmin/oktaonprem

docker run -it oktaadmin/oktaonprem bash
yum localinstall OktaProvisioningAgent-01.01.00.x86_64.rpm
```


### Edit `/opt/OktaProvisioningAgent/configure_agent.sh` to allow http

(From https://support.okta.com/help/s/article/30093436-Creating-SCIM-Connectors)
> Using http
>
> Before choosing this method, please note that the use of http is not recommended. Okta highly recommends the more secure option of https.
> 1. From the `configure_agent.sh` file, search for Configuring Okta Provisioning agent.
> 2. Add the command line argument -allowHttp true \ to the list of commands.

It should look like this:
```
echo "Configuring Okta Provisioning agent"
$JAVA -Dagent_home=$installprefix -jar $installprefix/bin/OktaProvisioningAgent.jar \
-mode register \
-env $environment \
-subdomain $subdomain \
-configFilePath $ConfigFile \
-noInstance true \
-proxyEnabled "$proxyEnabled" \
-proxyUrl "$proxyUrl" \
-proxyUser "$proxyUser" \
-proxyPassword "$proxyPass" \
-allowHttp true
```

### Configure and start agent

execute as root:

```
/opt/OktaProvisioningAgent/configure_agent.sh
service OktaProvisioningAgent start
```

## Keycloak Admin Api Java client
* Gist example: https://gist.github.com/ThomasVitale/b1f9b166277721582ce78ff1f7ec5873
* Java docs: https://www.keycloak.org/docs-api/8.0/javadocs/index.html (find org.keycloak.admin.client)

## Testing Scim Connector Resources

It looks  like we can orchestrate some tests against our connector with keycloak stood up

Need to grok these resources
* https://support.okta.com/help/s/article/80731786-Testing-Your-SCIM-Server-or-SCIM-Connector
* https://developer.okta.com/docs/guides/build-provisioning-integration/test-scim-api/

What is this link: https://support.okta.com/help/s/article/29448976-Configuring-On-Premises-Provisioning

## Ongoing Questions for Okta PS

1. Review SCIMimpl -> how are methods used?
1. why all the filters?


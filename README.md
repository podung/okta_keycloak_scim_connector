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
* https://support.okta.com/servlet/fileField?retURL=/articles/Knowledge_Article/46749316-On-Premises-Provisioning-Deployment-Guide&entityId=ka0F0000000AamoIAC&field=File_Attachment__Body__s

What is this link: https://support.okta.com/help/s/article/29448976-Configuring-On-Premises-Provisioning

## Ongoing Questions for Okta PS - or to research

1. Review SCIMimpl -> how are methods used?
  * why all the filters?
1. How are passwords stored in Okta that my plaintext password can be pushed to OPP?
1. OPP doesn't seem to be pushing existing users in group when adding a "push group" and when group assignment is set up.  I have to add them to the group after it is all setup, then they flush over.  WHat's going on?
1. Are emails required for Okta line service workers?  What format will we use?
1. How do we setup multi-org (tenant) hub and spoke setup?
1. How do public/priv signing keys work for hub and spoke architecture?  We need cloud services to accept JWT from any tenant, but in store doesnt matter
  * are there multiple metadata endpoints
  * are there multiple auth servers?
1. Connector -> filters 0 vs 1 based on okta side?  What about keycloak?
1. Group Push vs Assignments.  How to get the initial users for a group push down to an app? See "Limitation":  https://help.okta.com/en/prod/Content/Topics/Directory/Directory_Using_Group_Push.htm
  * implication: if you need to rebuild store server, and create a new OPP agent and a new keycloak, how to get the initial import over?  Do you really have to clear the push group membership and re-add people?
  * "Any group members that you want to push to the target app MUST be previously provisioned and assigned to the target app. As an Okta-mastered group, changes should never be made from the target app." - how do we provision with OPP?
1. What is the flow for removing users from a group.
  * There are two distinct group update calls during this process.  One has the list of current members and the second one has an empty list of members.
    *  What is the expected response from these calls?
  * Similar question from adding users
    * The first call has all members  the second call has just the new user to add

Links for SCIM
* https://developer.okta.com/docs/reference/scim/scim-20/#scim-user-operations
* https://developer.okta.com/docs/concepts/scim/#how-does-okta-help

Links for how password is stored
* https://www.okta.com/security
* https://www.okta.com/faq/


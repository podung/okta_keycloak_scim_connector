package com.okta.scim.server.example;

import com.okta.scim.server.capabilities.UserManagementCapabilities;
import com.okta.scim.server.exception.DuplicateGroupException;
import com.okta.scim.server.exception.EntityNotFoundException;
import com.okta.scim.server.exception.OnPremUserManagementException;
import com.okta.scim.server.service.SCIMService;
import com.okta.scim.util.model.*;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.ws.rs.core.Response;
import java.util.*;


/**
 * An example to show how to integrate with Okta using the SCIM SDK.
 * <p>
 * <b>Note:</b> This class is not performant, concise, or complete.
 * It was written to show how to work with the data model of the SDK.
 * <p>
 * <code>SCIMUser</code> and <code>SCIMGroup</code> objects which are part of the data model of the SDK are used to interact with the SDK.
 * <p>
 * <b>SCIMUser</b>
 * <p>
 * <p>
 * A <code>SCIMUser</code> has a set of standard properties that map to the SCIM Core Schema properties. The standard properties of
 * <code>SCIMUser</code> are converted to and from an Okta AppUser are the userName, password, id (converted into the
 * Okta AppUser's externalId), name (name.familyName and name.givenName), emails (primary and secondary), phone numbers (primary), and status.
 * <p>
 * This is how you can set the standard properties:
 * <ol type="a">
 * <li><code>SCIMUser user = new SCIMUser(); //Create an empty user</code></li>
 * <li><code>user.setUserName("someValue"); //Start setting the values for the standard properties</code></li>
 * <li><code>user.setId("someOtherValue");</code></li>
 * <li><code>user.setActive(true);</code></li>
 * <li><code>Collection&lt;Email&gt; emails = new ArrayList&lt;Email&gt;(); //emails, phone numbers and group memberships are all collections.</code>
 * <p>
 * You need to create a collection like this
 * <pre>
 *    Email email1 = new Email("abc@eample.com", "work", true);
 *    emails.add(email1);</pre>
 * <li><code>user.setEmails(emails); //Set the array emails to the user</code></li>
 * <li><code>Name name = new Name("Mr. John Smith", "Smith", "John"); //Name is a complex property.</code>
 * <li><code> user.setName(name); //Set the name to the user </code></li>
 * </ol>
 *
 * <p>
 * Similarly, get these standard properties as follows:
 * <ol type="a">
 * <li><code>String userName = user.getUserName(); //Get a non-nested simple property like userName, Id, Password, Status, etc.</code></li>
 * <li><code>String firstName = user.getName().getFirstName(); //Get a nested simple property like firstName, lastName </code></li>
 * <li><code>Mail firstEntry = user.getEmails().get(0); //Get an array element</code>
 * <li><code>String firstEmail = firstEntry.getValue(); //Get the value of that array element</code></li>
 * </ol>
 * <p></p>
 * <p>
 * If you need to set properties in addition to the standard properties, you can set them as custom properties in a namespace different from
 * that of the standard properties. The custom namespace is based on the appName and the Universal Directory (UD) schema name you created on Okta. For example, if your appName is
 * <i>onprem_app</i> and the schema name is <i>custom</i>, the custom namespace would be <code>urn:okta:onprem_app:1.0:user:custom</code>.
 * In general, the custom namespace would of the
 * form <code>urn:okta:{appName}:1.0:user:{udSchemaName}</code>. You need to know your appName and the schema name if you want to use the custom namespaces. You can login to Okta
 * and go to your App to find this information.
 * </p>
 *
 * <p>The datatypes supported are <code>String</code>, <code>Boolean</code>, <code>Integer</code>, and <code>Double</code>.</p>
 *
 * <p>There are two ways to get and set CustomProperties. In the examples below, assume that the custom namespace is <code>urn:okta:onprem_app:1.0:user:custom</code>.</p>
 *
 * <ol>
 * <li>Access the customPropertiesMap of <code>SCIMUser</code> which is a CustomSchemaName to JsonNode map for all the custom properties.
 * <p>
 * <i>Example to Set a custom property</i><pre>
 * JsonNode node = new JsonNode(); //Create a Jackson Json Node
 * node.put("customField1", "abc"); //Set string value
 * node.put("customField2", 123);   //Set an int value
 * Map&lt;String, JsonNode&gt; customMap = new HashMap&lt;String, JsonNode&gt;(); //Create a new map to be set to the SCIMUser
 * customMap.put("urn:okta:onprem_app:1.0:user:custom", node); //Put an entry in the map with the URN
 * user.setCustomPropertiesMap(customMap); //Set the custom properties map to the SCIM user
 * </pre>
 * <p>
 * <i>Example to Get a custom property</i><pre>
 * String customField1 = user.getCustomPropertiesMap().get("urn:okta:onprem_app:1.0:user:custom").get("customField1").asText();
 * You need to traverse the Json Node if the custom field is nested.
 * </pre>
 *
 * <li>Use getters and setters &ndash; <code>getCustomStringProperty</code>, <code>getCustomIntProperty</code>, <code>setCustomStringProperty</code>, <code>setCustomIntProperty</code>, etc.
 * <p><i>Example to Set a custom property</i><pre>
 * user.setCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "abc"); //Set a root level custom property
 * //Set a nested custom property where the "subRoot" is the parent of "customField1" and "root" is the parent of "subRoot".
 * user.setCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "abc", "root", "subRoot");
 * </pre>
 * <p>
 * Similar methods including &ndash; <code>setCustomIntValue</code>, <code>setCustomDoubleValue</code>, <code>setCustomBooleanValue</code> exist for the other data types.
 * <p>
 * <i>Example to Get a custom property</i><pre>
 * user.getCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1"); //Get a root level custom property
 * user.getCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "root", "subRoot"); //Get a nested custom property
 * </pre>
 * </ol>
 * <p>
 * <p>
 * <b>SCIMGroup</b>
 * <p>
 * A <code>SCIMGroup</code> has three standard properties. The standard properties of <code>SCIMGroup</code> are converted to and from Okta AppGroup.
 * <ol>
 * <li><code>id</code> - converted to/from the externalId of the Okta AppGroup</li>
 * <li><code>displayName</code> - the display name for the group</li>
 * <li><code>members</code> - the users that are members of the group</li>
 * </ol>
 * This is how you can set the standard properties of <code>SCIMGroup</code>:
 * <ol type="a">
 * <li><code>SCIMGroup group = new SCIMGroup(); //Create an empty group</code></li>
 * <li><code>group.setDisplayName("someValue"); //Set the display name</code></li>
 * <li><code>group.setId("someOtherValue"); //Set the Id</code></li>
 * <li><code>Collection&lt;Membership&gt; groupMemberships = new ArrayList&lt;Membership&gt;(); //Create the collection for group members, detailed below.</code>
 * <pre>
 *    Membership member = new Membership("101", "user displayName"); //Create a member entry for a user whose ID is 101, and displayName is "user displayName"
 *    groupMemberships.add(member); //Add the member
 * </pre></li>
 * <li><code>group.setMembers(groupMemberships); //Set the members for the group</code> </li>
 * </ol>
 * <p>
 * Get these properties from a SCIMGroup, as follows:
 * <pre>
 *    String displayName = group.getDisplayName();
 *    Membership firstMember = group.getMembers().get(0);
 * </pre>
 * <p>
 * <p>
 * A <code>SCIMGroup</code> can also contain custom properties. Currently, only one custom property (<code>description</code>) is supported for an <code>SCIMGroup</code>. Note that the custom namespace
 * for the <code>SCIMGroup</code> is a constant and is not dependent on the appName.</p>
 * <p>
 * Get/Set the description of the <code>SCIMGroup</code>
 * <pre>
 *      group.setCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN, SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION, "Group Description"); //Set the value
 *      String description = group.getCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN, SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION); //Get the value
 * </pre>
 * <p>
 * <p>
 * <b>Exceptions</b>
 * <p>
 * Throw the following three exceptions, as shown in the implementations below, as noted.<p>
 * <ul>
 * <li><code>EntityNotFoundException</code> - If an update/delete/get request is received for /Users and /Groups but the resource with the ID is not found.
 * <li><code>DuplicateGroupException</code> - If we get a create request for /Groups but a duplicate group exists
 * </ul>
 * <b>Note:</b> For both the above exceptions, the SDK associates an error code and a message with the exception automatically.
 * <ul><li><code>OnPremUserManagementException</code> - If there is any other exceptional condition (You cannot access the remote application, remote application
 * threw an exception, etc).</ul>
 * <p>For <code>OnPremUserManagementException</code> you can create the exception with any of the following four different properties.
 * <ul>
 * <li>Internal Code - Any arbitrary code to associate with the exception.</li>
 * <li>Description - Description for the exception.</li>
 * <li>Helper URL - If there is any URL for referral to help fix the issue.</li>
 * <li>Exception - Any associated exception.</li>
 * </ul>
 * <p><strong>Note:</strong> For all exceptions described above, if the code throws the exception while
 * processing a UM operation,&nbsp;the exception is serialized into a json string that is visible in Okta UI,
 * as shown below.
 * <blockquote>
 * "Failed on 11-24-2013 11:15:25PM UTC: Unable to delete Group Push mapping target App group ogroup-1: Error
 * while deleting user group ogroup-1: The Provisioning Agent call to deleteGroup failed. Error code: 404, error: Not Found.
 * Errors reported by the connector : {"errors":[{"statusCode":404,"internalCode":"E0000048","description":"Cannot delete the group - Cannot find the group with the id [1004]","help_url":null}]}"
 * </blockquote>
 * <p>If you want to store/read users/groups from files, specify the absolute paths for the files in dispatcher-servlet.xml</p>
 *
 * @author rpamidimarri
 */
public class SCIMServiceImpl implements SCIMService {
  //Absolute path for users.json set in the dispatcher-servlet.xml
  private String usersFilePath;
  //Absolute path for groups.json set in the dispatcher-servlet.xml
  private String groupsFilePath;

  private static final Logger LOGGER = LoggerFactory.getLogger(SCIMServiceImpl.class);

  private static String keyCloakAdminUrl = "http://localhost:9090/auth";

  private static Keycloak keycloak = KeycloakBuilder
          .builder()
          .serverUrl(keyCloakAdminUrl)
          .realm("master")
          .clientId("admin-cli")
          .username("admin")
          .password("admin")
//                    .clientSecret("42533ef8-fe84-4090-9751-08d3e4b29ac3") // Don't need this if we use a "user" - but we were trying to get it to work with an machine auth client credentials flow instead of user flow - should investigate this more
          .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build())
          .build();

  public String getUsersFilePath() {
    return usersFilePath;
  }

  public void setUsersFilePath(String usersFilePath) {
    this.usersFilePath = usersFilePath;
  }

  public String getGroupsFilePath() {
    return groupsFilePath;
  }

  public void setGroupsFilePath(String groupsFilePath) {
    this.groupsFilePath = groupsFilePath;
  }

  /**
   * This method creates a user. All the standard attributes of the SCIM User can be retrieved by using the
   * getters on the SCIMStandardUser member of the SCIMUser object.
   * <p>
   * If there are custom schemas in the SCIMUser input, you can retrieve them by providing the name of the
   * custom property. (Example : SCIMUser.getStringCustomProperty("schemaName", "customFieldName")), if the
   * property of string type.
   * <p>
   * This method is invoked when a POST is made to /Users with a SCIM payload representing a user
   * to be created.
   * <p>
   * NOTE: While the user's group memberships will be populated by Okta, according to the SCIM Spec
   * (http://www.simplecloud.info/specs/draft-scim-core-schema-01.html#anchor4) that information should be
   * considered read-only. Group memberships should only be updated through calls to createGroup or updateGroup.
   *
   * @param user SCIMUser representation of the SCIM String payload sent by the SCIM client.
   * @return the created SCIMUser.
   * @throws OnPremUserManagementException
   */
  @Override
  public SCIMUser createUser(SCIMUser user) throws OnPremUserManagementException {

    // https://www.keycloak.org/docs-api/8.0/rest-api/index.html#_users_resource
    // POST {keycloak_uri}/{realm}/users

    // Okta Field Names -> KeyCloak field names

    // TODO: decide which fields are important to map back and forth
    // userName -> username
    // firstName -> firstName
    // lastName -> lastName
    // email -> email
    // secondEmail -> [DO NOT MAP]
    // mobilePhone -> attributes (Map) -> { "mobilePhone": value_here }
    // NOTE: this might mean adding the custom attribute to keycloak, skipping for now

    String keyCloakAdminUrl = "http://localhost:9090/auth";
//            String postUsersPath = "/users";
//            URI serverUrl = new URI(keyCloakAdminUrl + postUsersPath);

    // TODO: should this be promoted out?  Should this live outside the context of a webrequest?

    UserRepresentation userRepresentation = updateKeycloakUser(user, new UserRepresentation());

    Response response = keycloak.realm("master").users().create(userRepresentation);

    if (response.getStatusInfo().equals(Response.Status.CREATED)) {
      String path = response.getLocation().getPath();
      user.setId(path.substring(path.lastIndexOf('/') + 1));
    }

    return user;
  }


  private UserRepresentation updateKeycloakUser(SCIMUser scimUser, UserRepresentation userRepresentation) {
    CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
    credentialRepresentation.setValue(scimUser.getPassword());

    // TODO: Does this really allow us to set Keycloak Id to okta ID??
    userRepresentation.setId(scimUser.getId());
    userRepresentation.setUsername(scimUser.getUserName());
    userRepresentation.setFirstName(scimUser.getName().getFirstName());
    userRepresentation.setLastName(scimUser.getName().getLastName());
    userRepresentation.setEnabled(true);
    userRepresentation.setCredentials(Arrays.asList(credentialRepresentation));

    return userRepresentation;
  }

  /**
   * This method updates a user.
   * <p>
   * This method is invoked when a PUT is made to /Users/{id} with the SCIM payload representing a user to
   * be updated.
   * <p>
   * NOTE: While the user's group memberships will be populated by Okta, according to the SCIM Spec
   * (http://www.simplecloud.info/specs/draft-scim-core-schema-01.html#anchor4) that information should be
   * considered read-only. Group memberships should only be updated through calls to createGroup or updateGroup.
   *
   * @param id   the id of the SCIM user.
   * @param user SCIMUser representation of the SCIM String payload sent by the SCIM client.
   * @return the updated SCIMUser.
   * @throws OnPremUserManagementException
   */
  @Override
  public SCIMUser updateUser(String id, SCIMUser user) throws OnPremUserManagementException, EntityNotFoundException {
    // https://www.keycloak.org/docs-api/8.0/rest-api/index.html#_users_resource

    UsersResource usersResource = keycloak.realm("master").users();
    UserResource keycloakUserResource = usersResource.get(id);
    UserRepresentation keycloakUser = usersResource.get(id).toRepresentation();

    if (keycloakUser != null) {
      UserRepresentation userRepresentation = updateKeycloakUser(user, keycloakUser);
      keycloakUserResource.update(userRepresentation);

      return user;
    } else {
      throw new EntityNotFoundException();
    }
  }

  /**
   * Get all the users.
   * <p>
   * This method is invoked when a GET is made to /Users
   * In order to support pagination (So that the client and the server are not overwhelmed), this method supports querying based on a start index and the
   * maximum number of results expected by the client. The implementation is responsible for maintaining indices for the SCIM Users.
   *
   * @param pageProperties denotes the pagination properties
   * @param filter         denotes the filter
   * @return the response from the server, which contains a list of  users along with the total number of results, start index and the items per page
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMUserQueryResponse getUsers(PaginationProperties pageProperties, SCIMFilter filter) throws OnPremUserManagementException {

    LOGGER.warn("GETUSERS Get Users Filter = " + (filter != null ? filter.toString() : "null filter"));

    if(filter != null ){
      /*
        TODO: Joe, I added this filter because when I try to rerun the task for your user it is doing a search by user name.
        It successfully returns the user twice (why it makes the call twice I don't know).  The error is still saying that okta
        is unable to find the ID in the returned ScimUser.  I'm wondering if keycloak generates its own ID and okta is expecting that?
        I need to run.  If you have a chance to add it to github my email for my account is jmkend@gmail.com.  I'll try to throw together a gitignore file tonight.
       */
      SCIMUserQueryResponse response = new SCIMUserQueryResponse();
      // find by user name
      List<SCIMUser> users = new ArrayList<>();
      for (UserRepresentation representation :
        keycloak.realm("master").users().search(filter.getFilterValue())) {
        users.add(createSCIMUserFromKeycloakRepresentation(representation));
      }

      response.setScimUsers(users);
      return response;
    }


    return getUsers(pageProperties);

    // TODO the getUsers call that is coming down to update user joe.depung is using a filter looking for a matching name.
    // will need to implement a simple filter to match this.
////        if (filter != null) {
////            //get users based on a filter
////            users = getuserbyfilter(filter);
////            //example to show how to construct a scimuserqueryresponse and how to set stuff.
////            scimuserqueryresponse response = new scimuserqueryresponse();
////            //the total results in this case is set to the number of users. but it may be possible that
////            //there are more results than what is being returned => totalresults > users.size();
////            response.settotalresults(users.size());
////            //actual results which need to be returned
////            response.setscimusers(users);
////            //the input has some page properties => set the start index.
////            if (pageproperties != null) {
////                response.setstartindex(pageproperties.getstartindex());
////            }
////            return response;
////        } else {
//        }
  }

  private SCIMUserQueryResponse getUsers(PaginationProperties pageProperties) {
    LOGGER.info("GETUSERS called with properties " + pageProperties.toString());
    SCIMUserQueryResponse response = new SCIMUserQueryResponse();
    UsersResource usersResource = keycloak.realm("master").users();

    int totalResults = usersResource.count();
    response.setTotalResults(totalResults);
    List<UserRepresentation> keycloakUsers = new ArrayList<>();
    if (pageProperties != null) {
      // TODO: is pageProperties 0 or 1 based index?
      // TODO: is keycloak startIndex 0 or 1 based index?

      //Set the start index to the response.
      response.setStartIndex(pageProperties.getStartIndex());
      Integer startIndex = Math.toIntExact(pageProperties.getStartIndex());
      keycloakUsers = usersResource.list(startIndex, 100);
    } else {
      keycloakUsers = usersResource.list();
    }

    List<SCIMUser> users = new ArrayList();

    for (UserRepresentation user : keycloakUsers) {
      users.add(createSCIMUserFromKeycloakRepresentation(user));
    }

    //Set the actual results
    response.setScimUsers(users);
    LOGGER.info("USERS returned " + users.toString());
    LOGGER.info("RESPONSE" + response.toString());
    return response;
  }

  /**
   * A simple example of how to use <code>SCIMFilter</code> to return a list of users which match the filter criteria.
   * <p/>
   * An Admin who configures the UM would specify a SCIM field name as the UniqueId field name. This field and its value would be sent by Okta in the filter.
   * While implementing the connector, the below points should be noted about the filters.
   * <p/>
   * If you choose a single valued attribute as the UserId field name while configuring the App Instance on Okta,
   * you would get an equality filter here.
   * For example, if you choose userName, the Filter object below may represent an equality filter like "userName eq "someUserName""
   * If you choose the name.familyName as the UserId field name, the filter object may represent an equality filter like
   * "name.familyName eq "someLastName""
   * If you choose a multivalued attribute (email, for example), the <code>SCIMFilter</code> object below may represent an OR filter consisting of two sub-filters like
   * "email eq "abc@def.com" OR email eq "def@abc.com""
   * Of the few multi valued attributes part of the SCIM Core Schema (Like email, address, phone number), only email would be supported as a UserIdField name on Okta.
   * So, you would have to deal with OR filters only if you choose email.
   * <p/>
   * When you get a <code>SCIMFilter</code>, you should check the filter field name (And make sure it is the same field which was configured with Okta), value, condition, etc. as shown in the examples below.
   *
   * @param filter the SCIM filter
   * @return list of users that match the filter
   */
  private List<SCIMUser> getUserByFilter(SCIMFilter filter) {
    List<SCIMUser> users = new ArrayList<>();

    SCIMFilterType filterType = filter.getFilterType();

    if (filterType.equals(SCIMFilterType.EQUALS)) {
      //Example to show how to deal with an Equality filter
      users = getUsersByEqualityFilter(filter);
    } else if (filterType.equals(SCIMFilterType.OR)) {
      //Example to show how to deal with an OR filter containing multiple sub-filters.
      users = getUsersByOrFilter(filter);
    } else {
      LOGGER.error("The Filter " + filter + " contains a condition that is not supported");
    }
    return users;
  }

  /**
   * This is an example for how to deal with an OR filter. An OR filter consists of multiple sub equality filters.
   *
   * @param filter the OR filter with a set of sub filters expressions
   * @return list of users that match any of the filters
   */
  private List<SCIMUser> getUsersByOrFilter(SCIMFilter filter) {
    //An OR filter would contain a list of filter expression. Each expression is a SCIMFilter by itself.
    //Ex : "email eq "abc@def.com" OR email eq "def@abc.com""
    List<SCIMFilter> subFilters = filter.getFilterExpressions();
    LOGGER.info("OR Filter : " + subFilters);
    List<SCIMUser> users = new ArrayList<>();
    //Loop through the sub filters to evaluate each of them.
    //Ex : "email eq "abc@def.com""
//    for (SCIMFilter subFilter : subFilters) {
//      //Name of the sub filter (email)
//      String fieldName = subFilter.getFilterAttribute().getAttributeName();
//      //Value (abc@def.com)
//      String value = subFilter.getFilterValue();
      //For all the users, check if any of them have this email
//      for (Map.Entry<String, SCIMUser> entry : userMap.entrySet()) {
//        boolean userFound = false;
//        SCIMUser user = entry.getValue();
//        //In this example, since we assume that the field name configured with Okta is "email", checking if we got the field name as "email" here
//        if (fieldName.equalsIgnoreCase("email")) {
//          //Get the user's emails and check if the value is the same as in the filter
//          Collection<Email> emails = user.getEmails();
//          if (emails != null) {
//            for (Email email : emails) {
//              if (email.getValue().equalsIgnoreCase(value)) {
//                userFound = true;
//                break;
//              }
//            }
//          }
//        }
//        if (userFound) {
//          users.add(user);
//        }
//      }
//    }
    return users;
  }

  /**
   * This is an example of how to deal with an equality filter.<p>
   * If you choose a custom field/complex field (name.familyName) or any other singular field (userName/externalId), you should get an equality filter here.
   *
   * @param filter the EQUALS filter
   * @return list of users that match the filter
   */
  private List<SCIMUser> getUsersByEqualityFilter(SCIMFilter filter) {
    String fieldName = filter.getFilterAttribute().getAttributeName();
    String value = filter.getFilterValue();
    LOGGER.info("Equality Filter : Field Name [ " + fieldName + " ]. Value [ " + value + " ]");
    List<SCIMUser> users = new ArrayList<>();

//    //A basic example of how to return users that match the criteria
//    for (Map.Entry<String, SCIMUser> entry : userMap.entrySet()) {
//      SCIMUser user = entry.getValue();
//      boolean userFound = false;
//      //Ex : "userName eq "someUserName""
//      if (fieldName.equalsIgnoreCase("userName")) {
//        String userName = user.getUserName();
//        if (userName != null && userName.equals(value)) {
//          userFound = true;
//        }
//      } else if (fieldName.equalsIgnoreCase("id")) {
//        //"id eq "someId""
//        String id = user.getId();
//        if (id != null && id.equals(value)) {
//          userFound = true;
//        }
//      } else if (fieldName.equalsIgnoreCase("name")) {
//        String subFieldName = filter.getFilterAttribute().getSubAttributeName();
//        Name name = user.getName();
//        if (name == null || subFieldName == null) {
//          continue;
//        }
//        if (subFieldName.equalsIgnoreCase("familyName")) {
//          //"name.familyName eq "someFamilyName""
//          String familyName = name.getLastName();
//          if (familyName != null && familyName.equals(value)) {
//            userFound = true;
//          }
//        } else if (subFieldName.equalsIgnoreCase("givenName")) {
//          //"name.givenName eq "someGivenName""
//          String givenName = name.getFirstName();
//          if (givenName != null && givenName.equals(value)) {
//            userFound = true;
//          }
//        }
//      } else if (filter.getFilterAttribute().getSchema().equalsIgnoreCase(userCustomUrn)) { //Check that the Schema name is the Custom Schema name to process the filter for custom fields
//        /**
//         * The example below shows one of the two ways to get a custom property.<p>
//         * The other way is to use the getter directly to get the value - user.getCustomStringProperty("urn:okta:onprem_app:1.0:user:custom", fieldName, null) will get the value
//         * if the fieldName is a root element. If fieldName is a child of any other field, user.getCustomStringProperty("urn:okta:onprem_app:1.0:user:custom", fieldName, parentName)
//         * will get the value.
//         */
//        //"urn:okta:onprem_app:1.0:user:custom:departmentName eq "someValue""
//        Map<String, JsonNode> customPropertiesMap = user.getCustomPropertiesMap();
//        //Get the custom properties map (SchemaName -> JsonNode)
//        if (customPropertiesMap == null || !customPropertiesMap.containsKey(userCustomUrn)) {
//          continue;
//        }
//        //Get the JsonNode having all the custom properties for this schema
//        JsonNode customNode = customPropertiesMap.get(userCustomUrn);
//        //Check if the node has that custom field
//        if (customNode.has(fieldName) && customNode.get(fieldName).asText().equalsIgnoreCase(value)) {
//          userFound = true;
//        }
//      }
//
//      if (userFound) {
//        users.add(user);
//      }
//    }
    return users;
  }


  private SCIMUser createSCIMUserFromKeycloakRepresentation(UserRepresentation keycloakUser) {
    // TODO: email?  Phone?  Anything else?
    SCIMUser user = new SCIMUser();
    user.setUserName(keycloakUser.getUsername());
    user.setName(new Name(keycloakUser.getFirstName() + keycloakUser.getLastName(), keycloakUser.getLastName(), keycloakUser.getFirstName()));
    user.setId(keycloakUser.getId());
    user.setActive(true);

    return user;
  }

  /**
   * Get a particular user.
   * <p>
   * This method is invoked when a GET is made to /Users/{id}
   *
   * @param id the Id of the SCIM User
   * @return the user corresponding to the id
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMUser getUser(String id) throws OnPremUserManagementException, EntityNotFoundException {

    UsersResource usersResource = keycloak.realm("master").users();
    UserRepresentation keycloakUser = usersResource.get(id).toRepresentation();

    if (keycloakUser != null) {
      return createSCIMUserFromKeycloakRepresentation(keycloakUser);
    } else {
      //If you do not find a user/group by the ID, you can throw this exception.
      throw new EntityNotFoundException();
    }
  }

  /**
   * This method creates a group. All the standard attributes of the SCIM group can be retrieved by using the
   * getters on the SCIMStandardGroup member of the SCIMGroup object.
   * <p>
   * If there are custom schemas in the SCIMGroup input, you can retrieve them by providing the name of the
   * custom property. (Example : SCIMGroup.getCustomProperty("schemaName", "customFieldName"))
   * <p>
   * This method is invoked when a POST is made to /Groups with a SCIM payload representing a group
   * to be created.
   *
   * @param group SCIMGroup representation of the SCIM String payload sent by the SCIM client
   * @return the created SCIMGroup
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroup createGroup(SCIMGroup group) throws OnPremUserManagementException, DuplicateGroupException {
    LOGGER.info("ENTERING createGroup");
    GroupsResource groupsResource = keycloak.realm("master").groups();
    String name = group.getDisplayName();
    boolean duplicate = false;
    for (GroupRepresentation groupRepresentation : groupsResource.groups()) {
      if(name.equals(groupRepresentation.getName())) {
        duplicate = true;
        break;
      }
    }

    if (duplicate) {
      throw new DuplicateGroupException();
    }

    LOGGER.info("SCIM Display Name" + group.getDisplayName());
    // TODO map SCIM group to Okta group
    GroupRepresentation newGroup = new GroupRepresentation();
    // TODO: what to do about this?
    newGroup.setName(group.getDisplayName());

   Response response = groupsResource.add(newGroup);
   if (response.getLocation() != null && !StringUtils.isEmpty(response.getLocation())) {
     LOGGER.info(response.getLocation().toString());
   } else {
     LOGGER.info("NO LOCATION HEADER (prob not successful response). code: " + response.getStatus());
   }


   // TODO: replace this all with inspecting the response and grabbing response.getLocation and parsing out group id
   // https://github.com/keycloak/keycloak/blob/9eb2e1d845e3ef5d502c45c8182573497d88fb1e/testsuite/integration-arquillian/tests/base/src/main/java/org/keycloak/testsuite/admin/ApiUtil.java


   GroupRepresentation createdGroup = null;
   for(GroupRepresentation representation : groupsResource.groups()) {
     LOGGER.info("REPRESENTATION IN LOOP: " + representation.toString());
     LOGGER.info("Display ame IN LOOP: " + representation.getName());
     LOGGER.info("getId : " + representation.getId());
     LOGGER.info("group display name : " + group.getDisplayName());
     if(representation.getName().equals(group.getDisplayName())) {
       createdGroup = representation;
     }
   }

   if(createdGroup != null) {
     group.setId(createdGroup.getId());
     LOGGER.info("We made a new group " + createdGroup);
   } else {
     LOGGER.info("CREATED GROUP WAS NOT SET");
   }

    LOGGER.info(group.toString());
    LOGGER.info("keycloak groups after the fact " + groupsResource.groups().toString());
    return group;
  }

  /**
   * This method updates a group.
   * <p>
   * This method is invoked when a PUT is made to /Groups/{id} with the SCIM payload representing a group to
   * be updated.
   *
   * @param id    the id of the SCIM group
   * @param group SCIMGroup representation of the SCIM String payload sent by the SCIM client
   * @return the updated SCIMGroup
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroup updateGroup(String id, SCIMGroup group) throws OnPremUserManagementException {
    LOGGER.info("ENTERING updateGroup with ID " + id);
//    SCIMGroup existingGroup = groupMap.get(id);
//    if (existingGroup != null) {
//      groupMap.put(id, group);
////            save();
//      return group;
//    } else {
//      throw new EntityNotFoundException();
//    }
    return group;
  }

  /**
   * Get all the groups.
   * <p>
   * This method is invoked when a GET is made to /Groups
   * In order to support pagination (So that the client and the server) are not overwhelmed, this method supports querying based on a start index and the
   * maximum number of results expected by the client. The implementation is responsible for maintaining indices for the SCIM groups.
   *
   * @param pageProperties @see com.okta.scim.util.model.PaginationProperties An object holding the properties needed for pagination - startindex and the count.
   * @return SCIMGroupQueryResponse the response from the server containing the total number of results, start index and the items per page along with a list of groups
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroupQueryResponse getGroups(PaginationProperties pageProperties) throws OnPremUserManagementException {
    LOGGER.info("ENTERING getGroups");
    SCIMGroupQueryResponse response = new SCIMGroupQueryResponse();
    GroupsResource keycloakGroups = keycloak.realm("master").groups();

    long groupCount = keycloakGroups.count(true).getOrDefault("count", (long) 0);
    int totalResults = Math.toIntExact(groupCount);
    response.setTotalResults(totalResults);

    List<GroupRepresentation> groupRepresentations = new ArrayList<>();
    if (pageProperties != null) {
      //Set the start index
      response.setStartIndex(pageProperties.getStartIndex());
      groupRepresentations = keycloakGroups.groups(Math.toIntExact(pageProperties.getStartIndex()), pageProperties.getCount());
    } else {
      groupRepresentations = keycloakGroups.groups();
    }

    List<SCIMGroup> scimGroups = new ArrayList<>();
    for (GroupRepresentation groupRepresentation : groupRepresentations) {
      scimGroups.add(createSCIMGroupFromKeycloakGroup(groupRepresentation));
    }

    response.setScimGroups(scimGroups);
    return response;
  }

  private SCIMGroup createSCIMGroupFromKeycloakGroup(GroupRepresentation keycloakGroup) {
    SCIMGroup scimGroup = new SCIMGroup();
    scimGroup.setDisplayName(keycloakGroup.getName());
    scimGroup.setId(keycloakGroup.getId());
    return scimGroup;
  }

  /**
   * Get a particular group.
   * <p>
   * This method is invoked when a GET is made to /Groups/{id}
   *
   * @param id the Id of the SCIM group
   * @return the group corresponding to the id
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroup getGroup(String id) throws OnPremUserManagementException {
    LOGGER.info("ENTERING getGroup with ID " + id);
    GroupResource groupResource = keycloak.realm("master").groups().group(id);
    if (groupResource != null) {
      return createSCIMGroupFromKeycloakGroup(groupResource.toRepresentation());
    } else {
      //If you do not find a user/group by the ID, you can throw this exception.
      throw new EntityNotFoundException();
    }
  }

  /**
   * Delete a particular group.
   * <p>
   * This method is invoked when a DELETE is made to /Groups/{id}
   *
   * @param id the Id of the SCIM group
   * @throws OnPremUserManagementException
   */
  @Override
  public void deleteGroup(String id) throws OnPremUserManagementException, EntityNotFoundException {
    LOGGER.info("ENTERING deleteGroup");
//    if (groupMap.containsKey(id)) {
//      groupMap.remove(id);
////            save();
//    } else {
//      throw new EntityNotFoundException();
//    }
  }

  /**
   * Get all the Okta User Management capabilities that this SCIM Service has implemented.
   * <p>
   * This method is invoked when a GET is made to /ServiceProviderConfigs. It is called only when you are testing
   * or modifying your connector configuration from the Okta Application instance UM UI. If you change the return values
   * at a later time please re-test and re-save your connector settings to have your new return values respected.
   * <p>
   * These User Management capabilities help customize the UI features available to your app instance and tells Okta
   * all the possible commands that can be sent to your connector.
   *
   * @return all the implemented User Management capabilities.
   */
  @Override
  public UserManagementCapabilities[] getImplementedUserManagementCapabilities() {
    return UserManagementCapabilities.values();
  }

}


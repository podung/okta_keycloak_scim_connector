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
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;

import java.net.URI;
import java.util.*;

/**
 * An example to show how to integrate with Okta using the SCIM SDK.
 * <p>
 * <b>Note:</b> This class is not performant, concise, or complete. It was
 * written to show how to work with the data model of the SDK.
 * <p>
 * <code>SCIMUser</code> and <code>SCIMGroup</code> objects which are part of
 * the data model of the SDK are used to interact with the SDK.
 * <p>
 * <b>SCIMUser</b>
 * <p>
 * <p>
 * A <code>SCIMUser</code> has a set of standard properties that map to the SCIM
 * Core Schema properties. The standard properties of <code>SCIMUser</code> are
 * converted to and from an Okta AppUser are the userName, password, id
 * (converted into the Okta AppUser's externalId), name (name.familyName and
 * name.givenName), emails (primary and secondary), phone numbers (primary), and
 * status.
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
 * 
 * <pre>
 * Email email1 = new Email("abc@eample.com", "work", true);
 * emails.add(email1);
 * </pre>
 * 
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
 * <p>
 * </p>
 * <p>
 * If you need to set properties in addition to the standard properties, you can
 * set them as custom properties in a namespace different from that of the
 * standard properties. The custom namespace is based on the appName and the
 * Universal Directory (UD) schema name you created on Okta. For example, if
 * your appName is <i>onprem_app</i> and the schema name is <i>custom</i>, the
 * custom namespace would be <code>urn:okta:onprem_app:1.0:user:custom</code>.
 * In general, the custom namespace would of the form
 * <code>urn:okta:{appName}:1.0:user:{udSchemaName}</code>. You need to know
 * your appName and the schema name if you want to use the custom namespaces.
 * You can login to Okta and go to your App to find this information.
 * </p>
 *
 * <p>
 * The datatypes supported are <code>String</code>, <code>Boolean</code>,
 * <code>Integer</code>, and <code>Double</code>.
 * </p>
 *
 * <p>
 * There are two ways to get and set CustomProperties. In the examples below,
 * assume that the custom namespace is
 * <code>urn:okta:onprem_app:1.0:user:custom</code>.
 * </p>
 *
 * <ol>
 * <li>Access the customPropertiesMap of <code>SCIMUser</code> which is a
 * CustomSchemaName to JsonNode map for all the custom properties.
 * <p>
 * <i>Example to Set a custom property</i>
 * 
 * <pre>
 * JsonNode node = new JsonNode(); // Create a Jackson Json Node
 * node.put("customField1", "abc"); // Set string value
 * node.put("customField2", 123); // Set an int value
 * Map&lt;String, JsonNode&gt; customMap = new HashMap&lt;String, JsonNode&gt;(); // Create a new map to be set to the SCIMUser
 * customMap.put("urn:okta:onprem_app:1.0:user:custom", node); // Put an entry in the map with the URN
 * user.setCustomPropertiesMap(customMap); // Set the custom properties map to the SCIM user
 * </pre>
 * <p>
 * <i>Example to Get a custom property</i>
 * 
 * <pre>
 * String customField1 = user.getCustomPropertiesMap().get("urn:okta:onprem_app:1.0:user:custom").get("customField1").asText();
 * You need to traverse the Json Node if the custom field is nested.
 * </pre>
 *
 * <li>Use getters and setters &ndash; <code>getCustomStringProperty</code>,
 * <code>getCustomIntProperty</code>, <code>setCustomStringProperty</code>,
 * <code>setCustomIntProperty</code>, etc.
 * <p>
 * <i>Example to Set a custom property</i>
 * 
 * <pre>
 * user.setCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "abc"); // Set a root level custom
 *                                                                                          // property
 * // Set a nested custom property where the "subRoot" is the parent of
 * // "customField1" and "root" is the parent of "subRoot".
 * user.setCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "abc", "root", "subRoot");
 * </pre>
 * <p>
 * Similar methods including &ndash; <code>setCustomIntValue</code>,
 * <code>setCustomDoubleValue</code>, <code>setCustomBooleanValue</code> exist
 * for the other data types.
 * <p>
 * <i>Example to Get a custom property</i>
 * 
 * <pre>
 * user.getCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1"); // Get a root level custom property
 * user.getCustomStringValue("urn:okta:onprem_app:1.0:user:custom", "customField1", "root", "subRoot"); // Get a nested
 *                                                                                                      // custom
 *                                                                                                      // property
 * </pre>
 * </ol>
 * <p>
 * <p>
 * <b>SCIMGroup</b>
 * <p>
 * A <code>SCIMGroup</code> has three standard properties. The standard
 * properties of <code>SCIMGroup</code> are converted to and from Okta AppGroup.
 * <ol>
 * <li><code>id</code> - converted to/from the externalId of the Okta
 * AppGroup</li>
 * <li><code>displayName</code> - the display name for the group</li>
 * <li><code>members</code> - the users that are members of the group</li>
 * </ol>
 * This is how you can set the standard properties of <code>SCIMGroup</code>:
 * <ol type="a">
 * <li><code>SCIMGroup group = new SCIMGroup(); //Create an empty group</code></li>
 * <li><code>group.setDisplayName("someValue"); //Set the display name</code></li>
 * <li><code>group.setId("someOtherValue"); //Set the Id</code></li>
 * <li><code>Collection&lt;Membership&gt; groupMemberships = new ArrayList&lt;Membership&gt;(); //Create the collection for group members, detailed below.</code>
 * 
 * <pre>
 * Membership member = new Membership("101", "user displayName"); // Create a member entry for a user whose ID is 101,
 *                                                                // and displayName is "user displayName"
 * groupMemberships.add(member); // Add the member
 * </pre>
 * 
 * </li>
 * <li><code>group.setMembers(groupMemberships); //Set the members for the group</code>
 * </li>
 * </ol>
 * <p>
 * Get these properties from a SCIMGroup, as follows:
 * 
 * <pre>
 * String displayName = group.getDisplayName();
 * Membership firstMember = group.getMembers().get(0);
 * </pre>
 * <p>
 * <p>
 * A <code>SCIMGroup</code> can also contain custom properties. Currently, only
 * one custom property (<code>description</code>) is supported for an
 * <code>SCIMGroup</code>. Note that the custom namespace for the
 * <code>SCIMGroup</code> is a constant and is not dependent on the appName.
 * </p>
 * <p>
 * Get/Set the description of the <code>SCIMGroup</code>
 * 
 * <pre>
 * group.setCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN,
 *     SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION, "Group Description"); // Set the value
 * String description = group.getCustomStringValue(SCIMOktaConstants.APPGROUP_OKTA_CUSTOM_SCHEMA_URN,
 *     SCIMOktaConstants.OKTA_APPGROUP_DESCRIPTION); // Get the value
 * </pre>
 * <p>
 * <p>
 * <b>Exceptions</b>
 * <p>
 * Throw the following three exceptions, as shown in the implementations below,
 * as noted.
 * <p>
 * <ul>
 * <li><code>EntityNotFoundException</code> - If an update/delete/get request is
 * received for /Users and /Groups but the resource with the ID is not found.
 * <li><code>DuplicateGroupException</code> - If we get a create request for
 * /Groups but a duplicate group exists
 * </ul>
 * <b>Note:</b> For both the above exceptions, the SDK associates an error code
 * and a message with the exception automatically.
 * <ul>
 * <li><code>OnPremUserManagementException</code> - If there is any other
 * exceptional condition (You cannot access the remote application, remote
 * application threw an exception, etc).
 * </ul>
 * <p>
 * For <code>OnPremUserManagementException</code> you can create the exception
 * with any of the following four different properties.
 * <ul>
 * <li>Internal Code - Any arbitrary code to associate with the exception.</li>
 * <li>Description - Description for the exception.</li>
 * <li>Helper URL - If there is any URL for referral to help fix the issue.</li>
 * <li>Exception - Any associated exception.</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> For all exceptions described above, if the code throws
 * the exception while processing a UM operation,&nbsp;the exception is
 * serialized into a json string that is visible in Okta UI, as shown below.
 * <blockquote> "Failed on 11-24-2013 11:15:25PM UTC: Unable to delete Group
 * Push mapping target App group ogroup-1: Error while deleting user group
 * ogroup-1: The Provisioning Agent call to deleteGroup failed. Error code: 404,
 * error: Not Found. Errors reported by the connector :
 * {"errors":[{"statusCode":404,"internalCode":"E0000048","description":"Cannot
 * delete the group - Cannot find the group with the id
 * [1004]","help_url":null}]}" </blockquote>
 * <p>
 * If you want to store/read users/groups from files, specify the absolute paths
 * for the files in dispatcher-servlet.xml
 * </p>
 *
 * @author rpamidimarri
 */
public class SCIMServiceImpl implements SCIMService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SCIMServiceImpl.class);

  private Keycloak keycloak;
  private UsersResource usersResource;
  private GroupsResource groupsResource;

  @PostConstruct
  public void afterCreation() {
    String KEYCLOAK_ADMIN_URL = "http://localhost:9090/auth";
    String REALM_NAME = "master";
    keycloak = KeycloakBuilder.builder().serverUrl(KEYCLOAK_ADMIN_URL).realm(REALM_NAME).clientId("admin-cli")
        .username("admin").password("admin")
        // .clientSecret("42533ef8-fe84-4090-9751-08d3e4b29ac3") // Don't need this if
        // we use a "user" - but we were trying to get it to work with an machine auth
        // client credentials flow instead of user flow - should investigate this more
        .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build()).build();

    RealmResource masterRealm = keycloak.realm(REALM_NAME);
    groupsResource = masterRealm.groups();
    usersResource = masterRealm.users();
  }

  /**
   * This method creates a user. All the standard attributes of the SCIM User can
   * be retrieved by using the getters on the SCIMStandardUser member of the
   * SCIMUser object.
   * <p>
   * If there are custom schemas in the SCIMUser input, you can retrieve them by
   * providing the name of the custom property. (Example :
   * SCIMUser.getStringCustomProperty("schemaName", "customFieldName")), if the
   * property of string type.
   * <p>
   * This method is invoked when a POST is made to /Users with a SCIM payload
   * representing a user to be created.
   * <p>
   * NOTE: While the user's group memberships will be populated by Okta, according
   * to the SCIM Spec
   * (http://www.simplecloud.info/specs/draft-scim-core-schema-01.html#anchor4)
   * that information should be considered read-only. Group memberships should
   * only be updated through calls to createGroup or updateGroup.
   *
   * @param user SCIMUser representation of the SCIM String payload sent by the
   *             SCIM client.
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
    // NOTE: this might mean adding the custom attribute to keycloak, skipping for
    // now

    UserRepresentation userRepresentation = updateKeycloakUser(user, new UserRepresentation());

    Response response = usersResource.create(userRepresentation);

    if (response.getStatusInfo().equals(Response.Status.CREATED)) {
      String path = response.getLocation().getPath();
      user.setId(path.substring(path.lastIndexOf('/') + 1));
      return user;
    } else {
      throw new OnPremUserManagementException("409", "User Already Exists");
    }
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
    userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));

    return userRepresentation;
  }

  /**
   * This method updates a user.
   * <p>
   * This method is invoked when a PUT is made to /Users/{id} with the SCIM
   * payload representing a user to be updated.
   * <p>
   * NOTE: While the user's group memberships will be populated by Okta, according
   * to the SCIM Spec
   * (http://www.simplecloud.info/specs/draft-scim-core-schema-01.html#anchor4)
   * that information should be considered read-only. Group memberships should
   * only be updated through calls to createGroup or updateGroup.
   *
   * @param id   the id of the SCIM user.
   * @param user SCIMUser representation of the SCIM String payload sent by the
   *             SCIM client.
   * @return the updated SCIMUser.
   * @throws OnPremUserManagementException
   */
  @Override
  public SCIMUser updateUser(String id, SCIMUser user) throws OnPremUserManagementException, EntityNotFoundException {

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
   * This method is invoked when a GET is made to /Users In order to support
   * pagination (So that the client and the server are not overwhelmed), this
   * method supports querying based on a start index and the maximum number of
   * results expected by the client. The implementation is responsible for
   * maintaining indices for the SCIM Users.
   *
   * @param pageProperties denotes the pagination properties
   * @param filter         denotes the filter
   * @return the response from the server, which contains a list of users along
   *         with the total number of results, start index and the items per page
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMUserQueryResponse getUsers(PaginationProperties pageProperties, SCIMFilter filter)
      throws OnPremUserManagementException {

    if (filter != null) {
      LOGGER.debug("getUsers with filter: " + filter.toString());

      SCIMUserQueryResponse response = new SCIMUserQueryResponse();
      List<SCIMUser> users = new ArrayList<>();

      LOGGER.info("Checking Keycloak for users with username" + filter.getFilterValue());
      if (filter.getFilterAttribute().getAttributeName().equals("userName")) {

        LOGGER.info("Calling Keycloak to get all users matching filter");
        List<UserRepresentation> allMatchingUsers = usersResource.search(filter.getFilterValue());

        List<UserRepresentation> returnUsers;
        LOGGER.debug("  received " + allMatchingUsers.size() + " users from Keycloak");

        // TODO: ensure we don't have an off-by-one issue with Okta and keycloak and our
        // code in this pagination logic
        if (pageProperties != null) {
          LOGGER.debug("applying pagination logic to all filtered users returned from Keycloak");
          int count = Math.toIntExact(pageProperties.getCount());
          int startIndex = Math.toIntExact(pageProperties.getStartIndex());
          int matchingSize = allMatchingUsers.size();
          int proposedEndIndex = count + startIndex;
          int endIndex = proposedEndIndex > matchingSize ? matchingSize : proposedEndIndex;

          response.setTotalResults(matchingSize);

          returnUsers = allMatchingUsers.subList(startIndex - 1, endIndex);
          LOGGER.debug("filtered users collection down to " + returnUsers.size() + " users");
        } else {
          LOGGER.debug("no pagination params passed, so returning all users");
          returnUsers = allMatchingUsers;
        }

        for (UserRepresentation representation : returnUsers) {
          users.add(createSCIMUserFromKeycloakRepresentation(representation));
        }
      } else {
        String attributeName = filter.getFilterAttribute().getAttributeName();
        LOGGER.error("only supported filter is Okta userName, received: " + attributeName);
        throw new OnPremUserManagementException("filter not supported", "Filter Name: " + attributeName);
      }

      response.setScimUsers(users);
      LOGGER.debug("Returning from getUsers with filtered-paginated result");
      return response;
    }

    return getUnfilteredUsers(pageProperties);
  }

  private SCIMUserQueryResponse getUnfilteredUsers(PaginationProperties pageProperties) {
    LOGGER.info("GETUSERS called");
    SCIMUserQueryResponse response = new SCIMUserQueryResponse();

    int totalResults = usersResource.count();
    response.setTotalResults(totalResults);
    List<UserRepresentation> keycloakUsers = new ArrayList<>();
    if (pageProperties != null) {
      LOGGER.info("Page Properties " + String.valueOf(pageProperties.getStartIndex()) + " "
          + String.valueOf(pageProperties.getCount()));
      // TODO: is pageProperties 0 or 1 based index?
      // TODO: is keycloak startIndex 0 or 1 based index?

      // Set the start index to the response.
      response.setStartIndex(pageProperties.getStartIndex());
      Integer startIndex = Math.toIntExact(pageProperties.getStartIndex());
      keycloakUsers = usersResource.list(startIndex, pageProperties.getCount());
    } else {
      keycloakUsers = usersResource.list();
    }

    List<SCIMUser> users = new ArrayList<>();

    for (UserRepresentation user : keycloakUsers) {
      users.add(createSCIMUserFromKeycloakRepresentation(user));
    }

    // Set the actual results
    response.setScimUsers(users);
    return response;
  }

  private SCIMUser createSCIMUserFromKeycloakRepresentation(UserRepresentation keycloakUser) {
    // TODO: email? Phone? Anything else?
    SCIMUser user = new SCIMUser();
    user.setUserName(keycloakUser.getUsername());
    user.setName(new Name(keycloakUser.getFirstName() + keycloakUser.getLastName(), keycloakUser.getLastName(),
        keycloakUser.getFirstName()));
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

    try {
      UserRepresentation keycloakUser = usersResource.get(id).toRepresentation();

      if (keycloakUser != null) {
        return createSCIMUserFromKeycloakRepresentation(keycloakUser);
      } else {
        throw new EntityNotFoundException();
      }
    } catch (javax.ws.rs.NotFoundException ex) {
      throw new EntityNotFoundException();
    } catch (Exception ex) {
      LOGGER.error(ex.getMessage() + '\n' + Arrays.toString(ex.getStackTrace()));
      throw new OnPremUserManagementException("Error in getUser implementation", ex.getMessage());
    }
  }

  /**
   * This method creates a group. All the standard attributes of the SCIM group
   * can be retrieved by using the getters on the SCIMStandardGroup member of the
   * SCIMGroup object.
   * <p>
   * If there are custom schemas in the SCIMGroup input, you can retrieve them by
   * providing the name of the custom property. (Example :
   * SCIMGroup.getCustomProperty("schemaName", "customFieldName"))
   * <p>
   * This method is invoked when a POST is made to /Groups with a SCIM payload
   * representing a group to be created.
   *
   * @param group SCIMGroup representation of the SCIM String payload sent by the
   *              SCIM client
   * @return the created SCIMGroup
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroup createGroup(SCIMGroup group) throws OnPremUserManagementException, DuplicateGroupException {
    LOGGER.debug("ENTERING createGroup");
    GroupsResource groupsResource = keycloak.realm("master").groups();
    String groupName = group.getDisplayName();
    boolean duplicate = false;
    for (GroupRepresentation groupRepresentation : groupsResource.groups()) {
      if (groupName.equals(groupRepresentation.getName())) {
        duplicate = true;
        break;
      }
    }

    if (duplicate) {
      throw new DuplicateGroupException();
    }

    GroupRepresentation newGroup = new GroupRepresentation();
    newGroup.setName(groupName);

    Response response = groupsResource.add(newGroup);
    String createdGroupId = getCreatedId(response);

    Collection<Membership> memberships = group.getMembers();
    if (memberships != null) {
      addUsersToGroup(memberships, createdGroupId, groupName);
    }

    group.setId(createdGroupId);
    LOGGER.debug("Returning from createGroup");
    return group;
  }

  private void addUsersToGroup(Collection<Membership> memberships, String groupId, String groupName) {
    for (Membership membership : memberships) {
      String username = membership.getDisplayName();
      UserResource resource = usersResource.get(membership.getId());
      if (resource.toRepresentation() == null) {
        LOGGER.info(String.format("User %s with ID %s not found while attempting to add to group %s", username,
            membership.getId(), groupName));
      } else {
        LOGGER.info(String.format("Adding user %s to group %s", username, groupName));
        resource.joinGroup(groupId);
      }
    }
  }

  private static String getCreatedId(Response response) {
    URI location = response.getLocation();
    if (!response.getStatusInfo().equals(Status.CREATED)) {
      StatusType statusInfo = response.getStatusInfo();
      response.bufferEntity();
      String body = response.readEntity(String.class);
      throw new WebApplicationException("Create method returned status " + statusInfo.getReasonPhrase() + " (Code: "
          + statusInfo.getStatusCode() + "); expected status: Created (201). Response body: " + body, response);
    }
    if (location == null) {
      return null;
    }
    String path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /**
   * This method updates a group.
   * <p>
   * This method is invoked when a PUT is made to /Groups/{id} with the SCIM
   * payload representing a group to be updated.
   *
   * @param id    the id of the SCIM group
   * @param group SCIMGroup representation of the SCIM String payload sent by the
   *              SCIM client
   * @return the updated SCIMGroup
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroup updateGroup(String id, SCIMGroup group) throws OnPremUserManagementException {
    LOGGER.info("ENTERING updateGroup with ID " + id);
    GroupResource groupResource = groupsResource.group(id);

    if (groupResource == null) {
      throw new EntityNotFoundException();
    }

    /*
      not sure how updates are supposed to work.  For now we have to completely remove the members of a group and readd them.
    */

    GroupRepresentation groupToUpdate = groupResource.toRepresentation();
    groupToUpdate.setName(group.getDisplayName());


    if (group.getMembers() != null) {
//      for(UserRepresentation user : usersResource.list()) {
//        // TODO for some reason the groups tied to the user are null
//        // now sure how to handle removal
//        usersResource.get(user.getId()).leaveGroup(id);
//      }
      // remove existing members of the group
      addUsersToGroup(group.getMembers(), id, group.getDisplayName());
    }

    groupResource.update(groupToUpdate);
    return group;
  }

  /**
   * Get all the groups.
   * <p>
   * This method is invoked when a GET is made to /Groups In order to support
   * pagination (So that the client and the server) are not overwhelmed, this
   * method supports querying based on a start index and the maximum number of
   * results expected by the client. The implementation is responsible for
   * maintaining indices for the SCIM groups.
   *
   * @param pageProperties @see com.okta.scim.util.model.PaginationProperties An
   *                       object holding the properties needed for pagination -
   *                       startindex and the count.
   * @return SCIMGroupQueryResponse the response from the server containing the
   *         total number of results, start index and the items per page along
   *         with a list of groups
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroupQueryResponse getGroups(PaginationProperties pageProperties) throws OnPremUserManagementException {
    LOGGER.info("ENTERING getGroups");
    SCIMGroupQueryResponse response = new SCIMGroupQueryResponse();

    long groupCount = groupsResource.count(true).getOrDefault("count", (long) 0);
    int totalResults = Math.toIntExact(groupCount);
    response.setTotalResults(totalResults);

    List<GroupRepresentation> groupRepresentations = new ArrayList<>();
    if (pageProperties != null) {
      LOGGER.info("pagination exists with start index " + pageProperties.getStartIndex() + " and count "
          + pageProperties.getCount());
      // Set the start index
      response.setStartIndex(pageProperties.getStartIndex());
      groupRepresentations = groupsResource.groups(Math.toIntExact(pageProperties.getStartIndex()),
          pageProperties.getCount());
    } else {
      LOGGER.info("No Pagination - returning all groups");
      groupRepresentations = groupsResource.groups();
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
    GroupResource groupResource = groupsResource.group(id);
    if (groupResource != null) {
      return createSCIMGroupFromKeycloakGroup(groupResource.toRepresentation());
    } else {
      // If you do not find a user/group by the ID, you can throw this exception.
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
    GroupResource groupResource = groupsResource.group(id);
    if (groupResource != null) {
        groupResource.remove();
    } else {
      // If you do not find a user/group by the ID, you can throw this exception.
      throw new EntityNotFoundException();
    }
  }

  /**
   * Get all the Okta User Management capabilities that this SCIM Service has
   * implemented.
   * <p>
   * This method is invoked when a GET is made to /ServiceProviderConfigs. It is
   * called only when you are testing or modifying your connector configuration
   * from the Okta Application instance UM UI. If you change the return values at
   * a later time please re-test and re-save your connector settings to have your
   * new return values respected.
   * <p>
   * These User Management capabilities help customize the UI features available
   * to your app instance and tells Okta all the possible commands that can be
   * sent to your connector.
   *
   * @return all the implemented User Management capabilities.
   */
  @Override
  public UserManagementCapabilities[] getImplementedUserManagementCapabilities() {
    return UserManagementCapabilities.values();
  }

}

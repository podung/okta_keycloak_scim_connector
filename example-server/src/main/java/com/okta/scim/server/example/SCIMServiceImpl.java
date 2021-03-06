package com.okta.scim.server.example;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import java.util.stream.Collectors;

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

  // Absolute path for users.json set in the dispatcher-servlet.xml
  private String usersFilePath;
  // Absolute path for groups.json set in the dispatcher-servlet.xml
  private String groupsFilePath;

  private Keycloak keycloak;
  private UsersResource usersResource;
  private GroupsResource groupsResource;

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
    LOGGER.debug("Entering createUser: " + user.getUserName());

    UserRepresentation userRepresentation = updateKeycloakUser(user, new UserRepresentation());

    Response response = usersResource.create(userRepresentation);

    if (response.getStatusInfo().equals(Response.Status.CREATED)) {
      // TODO: Log here
      String path = response.getLocation().getPath();
      user.setId(path.substring(path.lastIndexOf('/') + 1));
      // TODO: Log here
      return user;
    } else {
      LOGGER.debug("  User already exists, throwing OnPremUserManagementException");
      throw new OnPremUserManagementException("409", "User Already Exists");
    }
  }

  private UserRepresentation updateKeycloakUser(SCIMUser scimUser, UserRepresentation userRepresentation) {
    // TODO: Log here
    userRepresentation.setId(scimUser.getId());
    userRepresentation.setUsername(scimUser.getUserName());
    userRepresentation.setFirstName(scimUser.getName().getFirstName());
    userRepresentation.setLastName(scimUser.getName().getLastName());
    userRepresentation.setEnabled(true);

    String newPassword = scimUser.getPassword();
    if (newPassword != null && !newPassword.isEmpty()) {
      CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
      credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
      credentialRepresentation.setValue(newPassword);

      userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));
    }

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
    LOGGER.debug("Entering updateUser: " + user.getUserName());
    UserResource keycloakUserResource = usersResource.get(id);
    UserRepresentation keycloakUser = keycloakUserResource.toRepresentation();

    if (keycloakUser != null) {
      UserRepresentation userRepresentation = updateKeycloakUser(user, keycloakUser);
      // TODO: Log here
      keycloakUserResource.update(userRepresentation);

      return user;
    } else {
      LOGGER.debug("  Could not find user to update in KeyCloak");
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
   * with the total number of results, start index and the items per page
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMUserQueryResponse getUsers(PaginationProperties pageProperties, SCIMFilter filter)
    throws OnPremUserManagementException {
    LOGGER.info("getUsers Called");
    if (filter != null) {
      return getFilteredUsers(pageProperties, filter);
    } else {
      return getScimUsersToReturn(pageProperties, usersResource.list());
    }
  }

  private SCIMUserQueryResponse getFilteredUsers(PaginationProperties pageProperties, SCIMFilter filter) {
    LOGGER.debug("getFilteredUsers with filter: " + filter.toString());

    LOGGER.info("Checking Keycloak for users with username" + filter.getFilterValue());
    if (filter.getFilterAttribute().getAttributeName().equals("userName")) {

      LOGGER.info("Calling Keycloak to get all users matching filter");
      List<UserRepresentation> allMatchingUsers = usersResource.search(filter.getFilterValue());
      LOGGER.debug("  received " + allMatchingUsers.size() + " users from Keycloak");

      return getScimUsersToReturn(pageProperties, allMatchingUsers);
    } else {
      String attributeName = filter.getFilterAttribute().getAttributeName();
      LOGGER.error("only supported filter is Okta userName, received: " + attributeName);
      throw new OnPremUserManagementException("filter not supported", "Filter Name: " + attributeName);
    }

  }

  private SCIMUserQueryResponse getScimUsersToReturn(PaginationProperties pageProperties, List<UserRepresentation> allMatchingUsers) {
    List<UserRepresentation> returnUsers;
    SCIMUserQueryResponse response = new SCIMUserQueryResponse();
    LOGGER.info("getScimUsersToReturns");

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

    List<SCIMUser> users = new ArrayList<>();
    for (UserRepresentation representation : returnUsers) {
      users.add(createSCIMUserFromKeycloakRepresentation(representation));
    }

    response.setScimUsers(users);
    return response;
  }

  private SCIMUser createSCIMUserFromKeycloakRepresentation(UserRepresentation keycloakUser) {
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
    LOGGER.debug("Entering getUser: " + id);

    // TODO: litter this thing with some good debug logs
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

    // TODO: litter this thing with good debug logs too
    GroupRepresentation foundGroup = getKeycloakGroupByName(groupName);

    if (foundGroup != null) {
      throw new DuplicateGroupException();
    }

    String createdGroupId = createTheGroup(groupName);

    Collection<Membership> memberships = group.getMembers();
    if (memberships != null) {
      addUsersToGroup(memberships, createdGroupId, groupName);
    }

    // NOTE: if users are not found, what are we supposed to do?
    group.setId(createdGroupId);
    LOGGER.debug("Returning from createGroup");
    return group;
  }

  private GroupRepresentation getKeycloakGroupByName(String name) {
    for (GroupRepresentation groupRepresentation : groupsResource.groups()) {
      if (name.equals(groupRepresentation.getName())) {
        return groupRepresentation;
      }
    }

    return null;
  }

  private String createTheGroup(String groupName) {
    LOGGER.debug("  Creating a KeyCloak group with name: " + groupName);
    GroupRepresentation newGroup = new GroupRepresentation();
    newGroup.setName(groupName);

    Response response = groupsResource.add(newGroup);
    String createdGroupId = getCreatedId(response);

    LOGGER.debug("    keycloak id for new group: " + createdGroupId);
    return createdGroupId;
  }

  private void addUsersToGroup(Iterable<Membership> memberships, String groupId, String groupName) {
    for (Membership membership : memberships) {
      LOGGER.debug("    Adding " + membership.getDisplayName() + " to " + groupName);
      String username = membership.getDisplayName();
      UserResource resource = usersResource.get(membership.getId());
      if (resource.toRepresentation() == null) {
        LOGGER.debug(String.format("User %s with ID %s not found while attempting to add to group %s", username,
          membership.getId(), groupName));
      } else {
        LOGGER.debug(String.format("Adding user %s to group %s", username, groupName));
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
    // TODO: am I still getting a 500 when I remove all membership from group

    LOGGER.debug("ENTERING updateGroup with ID " + id + "(incoming name: " + group.getDisplayName() + ")");

    Collection<Membership> members = group.getMembers();

    logDesiredGroupMembership(members);

    GroupResource foundGroup = null;

    try {
      LOGGER.debug(" Attempting to find group with id " + id);
      foundGroup = groupsResource.group(id);
    } catch (javax.ws.rs.NotFoundException ex) {
      LOGGER.debug("  Got a 404 not found while trying to find the group");
      throw new EntityNotFoundException();
    }

    if (foundGroup == null) {
      LOGGER.debug("  Did not find group with ID " + id);
      throw new EntityNotFoundException();
    }

    try {
      LOGGER.debug("  Retrieving Keycloak group " + id);
      GroupResource groupResource = groupsResource.group(id);

      if (groupResource != null) {
        LOGGER.debug("  Found group with ID " + id + "(name: " + groupResource.toRepresentation().getName() + ")");

        List<UserRepresentation> existingMembers = groupResource.members();
        Collection<Membership> requestedMembers = group.getMembers() != null ? group.getMembers() : Collections.emptyList();

        Set<String> existingIds = Sets.newHashSet(existingMembers.stream().map(u -> u.getId()).collect(Collectors.toList()));
        Set<String> requestedIds = Sets.newHashSet(group.getMembers().stream().map(u -> u.getId()).collect(Collectors.toSet()));

        // any user in existing, not in requested ... mark for removal
        Set<String> idsToRemove = Sets.difference(existingIds, requestedIds);
        // any user in requested, not in existing ... mark for addition
        Set<String> idsToAdd = Sets.difference(requestedIds, existingIds);

        LOGGER.debug("  Removing users no longer in the group");
        for (String userId : idsToRemove) {
          // TODO: should I get the users name that I'm removing????
          LOGGER.debug("  Removing user with id: " + userId);
          usersResource.get(userId).leaveGroup(id);


          if (idsToAdd.size() > 0) {
            LOGGER.debug("  Adding users to group");

            Iterable<Membership> membersToAdd = Iterables.filter(requestedMembers, x -> idsToAdd.contains(x.getId()));
            addUsersToGroup(membersToAdd, id, group.getDisplayName());
          }

          // TODO: do we need to update the group info here??? -
          //    GroupRepresentation groupToUpdate = groupResource.toRepresentation();
          //    groupToUpdate.setName(group.getDisplayName());
          //    groupResource.update(groupToUpdate);
          //    group.setId(createdGroupId);

          return group;
        }
      }
    }
    catch(javax.ws.rs.NotFoundException ex){
      // TODO: debug - find out why when groupResource is != null, there is no group to remove?????? - so it blows up????
      LOGGER.debug("  Got a 404 not found while trying to remove the group");
      LOGGER.debug("  Did not find group with ID " + id + ", so nothing to remove before re-creating");
    }

    throw new EntityNotFoundException();
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
   * total number of results, start index and the items per page along
   * with a list of groups
   * @throws com.okta.scim.server.exception.OnPremUserManagementException
   */
  @Override
  public SCIMGroupQueryResponse getGroups(PaginationProperties pageProperties) throws OnPremUserManagementException {
    // TODO: should we add the group membership here????


    LOGGER.debug("ENTERING getGroups");
    SCIMGroupQueryResponse response = new SCIMGroupQueryResponse();

    long groupCount = groupsResource.count(true).getOrDefault("count", (long) 0);
    int totalResults = Math.toIntExact(groupCount);
    response.setTotalResults(totalResults);

    List<GroupRepresentation> groupRepresentations = new ArrayList<>();
    if (pageProperties != null) {
      LOGGER.debug("pagination exists with start index " + pageProperties.getStartIndex() + " and count "
        + pageProperties.getCount());
      // Set the start index
      response.setStartIndex(pageProperties.getStartIndex());
      groupRepresentations = groupsResource.groups(Math.toIntExact(pageProperties.getStartIndex()),
        pageProperties.getCount());
    } else {
      LOGGER.debug("No Pagination - returning all groups");
      groupRepresentations = groupsResource.groups();
    }

    List<SCIMGroup> scimGroups = new ArrayList<>();
    for (GroupRepresentation groupRepresentation : groupRepresentations) {
      scimGroups.add(createSCIMGroupFromKeycloakGroup(groupRepresentation));
    }

    response.setScimGroups(scimGroups);
    return response;
  }

  private SCIMGroup createSCIMGroupFromKeycloakGroup2(GroupResource keycloakGroupResource) {
    GroupRepresentation keycloakGroup = keycloakGroupResource.toRepresentation();

    SCIMGroup scimGroup = new SCIMGroup();
    scimGroup.setDisplayName(keycloakGroup.getName());
    scimGroup.setId(keycloakGroup.getId());

    ArrayList<Membership> memberList = new ArrayList<Membership>();

    for(UserRepresentation user : keycloakGroupResource.members()) {
      // TODO: should we add logs here???
      Membership memHolder = new Membership(user.getId(), user.getUsername());
      memberList.add(memHolder);
    };

    scimGroup.setMembers(memberList);

    return scimGroup;
  }

  private SCIMGroup createSCIMGroupFromKeycloakGroup(GroupRepresentation keycloakGroup) {
    SCIMGroup scimGroup = new SCIMGroup();
    scimGroup.setDisplayName(keycloakGroup.getName());
    scimGroup.setId(keycloakGroup.getId());

    ArrayList<Membership> memberList = new ArrayList<Membership>();

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
    LOGGER.debug("ENTERING getGroup with ID " + id);
    GroupResource groupResource = groupsResource.group(id);
    if (groupResource != null) {
      LOGGER.debug("  Found group " +  groupResource.toRepresentation().getName());

      for(UserRepresentation user : groupResource.members()) {
        // TODO for some reason the groups tied to the user are null
        // now sure how to handle removal
        LOGGER.debug("    found " + user.getUsername() + "(" + user.getId() + ") in group") ;
      };

      return createSCIMGroupFromKeycloakGroup2(groupResource);
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
    LOGGER.debug("ENTERING deleteGroup for group id: " + id);
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

  private void logDesiredGroupMembership(Collection<Membership> members) {
    if (members != null) {
      for(Membership membership : members) {
        LOGGER.debug("  requesting to ensure "  + membership.getDisplayName() + " (id: " + membership.getId() + ") is in the group");
      };
    } else {
      LOGGER.debug("  requesting to remove all users");
    }
  }
}


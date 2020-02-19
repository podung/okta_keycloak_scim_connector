alias createNewUser='java -jar scim-sdk-tests.jar -url http://localhost:8080/scim-server-example-01.03.02-SNAPSHOT/ -method createNewUser -file data/createNewUser.json'
alias createPendingUser='java -jar scim-sdk-tests.jar -url http://localhost:8080/scim-server-example-01.03.02-SNAPSHOT/ -method createPendingUser -file data/createPendingUser-withCustomExtension.json'
alias activateUser='java -jar scim-sdk-tests.jar -url http://localhost:8080/scim-server-example-01.03.02-SNAPSHOT/  -method activateUser -file data/activateUser.json'
alias checkuserExists='java -jar scim-sdk-tests.jar -url http://localhost:8080/scim-server-example-01.03.02-SNAPSHOT/  -method checkUserExists -arg userIdFieldName=userName -arg userIdFieldValue=myemail-pending@domain.com'


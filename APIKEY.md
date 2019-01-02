# APIKEY

### To be able to identify where requests are coming from and allow requests to be made, the use of an APIKEY can be configed by the following steps:

1. Add on the main resources/social-wallet-api.yaml config file the entry `apikey: <client-app-id>` under freecoin. 
2. When starting the server an APIKEY will be produced, if not already created, and assigned to the client-app-id in storage.
3. The APIKEY will be written on the apikey.yaml file on the HOME DIR of this project.
4. The APIKEY can be retrieved at any point either by reading the apikey.yaml file or by running the apikey.sh script (access to the storage for the user running the script is required)
5. If configured as above, any request to the Social Wallet API that does not contain the x-api-key header with the right api key, will return a 401 response.
6. When using the Social Wallet API through swagger, the button authenticate on the top of the swagger page, will lead to a form that needs to be filled in with the right api key to be send as header with every request.

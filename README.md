# Flow Wallet Android Project User Guide
This document is a guide for users who want to run the Flow Wallet Android project. Before starting, please make sure you have installed Android Studio and have a basic understanding of Android development.

### Step 1: Configure google-services.json
The `google-services.json` file is required to enable Firebase services such as Firebase Authentication and Firebase Cloud Messaging. To use these services, you need to provide your own google-services.json file.

You should have three different versions of the `google-services.json` file: one for debug, one for development, and one for release. Please place them in the following directories:

- app/src/debug/google-services.json
- app/src/dev/google-services.json
- app/src/release/google-services.json

### Step 2: Configure signing information
In order to generate a signed APK file, you need to configure your signing information. To do so, please add the following lines to your local.properties file:

```makefile
keyAlias=your_alias_name
keyPassword=your_password
storeFile=your_keystore_file_path
storePassword=your_keystore_password
```
Please replace the values with your own information. Note that the keystore file should be placed in the specified path.

### Step 3: Create the config file
The config file is used to store sensitive information such as encryption keys and project IDs. Please create a file named `key.properties` in the root directory. The file should contain the following information:

```makefile
DRIVE_AES_IV
DRIVE_AES_KEY
WALLET_CONNECT_PROJECT_ID
INSTABUG_TOKEN_DEV
INSTABUG_TOKEN_PROD
CROWDIN_PROJECT_ID
CROWDIN_API_TOKEN
CROWDIN_DISTRIBUTION
MIXPANEL_TOKEN_DEV
MIXPANEL_TOKEN_PROD
```

Please fill in the values for each item according to your project's requirements.

### Step 4: Run the project
Now you are ready to run the Flow Wallet Android project. Simply open the project in Android Studio and run it on an emulator or a physical device.

If you encounter any issues during the setup process, please refer to the project's documentation or contact the project's developer for assistance.

// Order Platform - Security stack (Step 4).
//
// The Azure equivalent of the AWS CloudFormation security template. It creates:
//   1. A Key Vault (secrets) — the analog of AWS Secrets Manager + KMS.
//   2. A user-assigned Managed Identity for the Spring Boot app — the analog of
//      the AWS IAM role.
//   3. A Managed Identity for the Azure Function consumer.
//   4. RBAC role assignments granting the app access to Key Vault, SQL,
//      Service Bus, and Redis.
//   5. (Commented) AKS cluster identity for the container runtime — the analog
//      of the AWS EKS roles.
//
// Deploy with:
//   az group create -n rg-order-platform -l westeurope
//   az deployment group create -g rg-order-platform -f security.bicep
//         -p applicationName=order-platform stage=dev

@description('Name used to prefix resources.')
param applicationName string = 'order-platform'

@description('Environment stage (used in resource naming).')
@allowed([
  'dev'
  'staging'
  'prod'
])
param stage string = 'dev'

@description('Azure region for the deployment.')
param location string = resourceGroup().location

// ---------------------------------------------------------------------------
// 1. Key Vault — application secrets (analog of AWS Secrets Manager + KMS)
// ---------------------------------------------------------------------------
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: '${applicationName}-${stage}-kv'
  location: location
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
  }
}

// ---------------------------------------------------------------------------
// 2. Managed Identity — Spring Boot application (analog of AWS IAM app role)
// ---------------------------------------------------------------------------
resource appIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${applicationName}-app-${stage}'
  location: location
}

// ---------------------------------------------------------------------------
// 3. Managed Identity — Azure Function consumer (analog of AWS Lambda role)
// ---------------------------------------------------------------------------
resource functionIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${applicationName}-function-${stage}'
  location: location
}

// ---------------------------------------------------------------------------
// 4. RBAC role assignments (analog of AWS IAM policies)
// ---------------------------------------------------------------------------

// Key Vault Secrets User: allow the app to read its secrets.
resource appKeyVaultAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, appIdentity.id, 'KeyVaultSecretsUser')
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      '4633458b-17de-408a-b874-0445c86b69e6') // Key Vault Secrets User
    principalId: appIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// Azure Service Bus Data Owner: allow the app to manage and send to the topic.
resource appServiceBusAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(resourceGroup().id, appIdentity.id, 'ServiceBusDataOwner')
  scope: resourceGroup()
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      '090c5cfd-751d-490a-840a-5ce3f5f45c46') // Azure Service Bus Data Owner
    principalId: appIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// Azure Service Bus Data Receiver: allow the function to receive messages.
resource functionServiceBusAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(resourceGroup().id, functionIdentity.id, 'ServiceBusDataReceiver')
  scope: resourceGroup()
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      '4f6d3b9b-027b-4f4c-9142-0e5a2a2247e0') // Azure Service Bus Data Receiver
    principalId: functionIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// ---------------------------------------------------------------------------
// Outputs
// ---------------------------------------------------------------------------
output keyVaultUri string = keyVault.properties.vaultUri
output appIdentityClientId string = appIdentity.properties.clientId
output appIdentityPrincipalId string = appIdentity.properties.principalId
output functionIdentityClientId string = functionIdentity.properties.clientId

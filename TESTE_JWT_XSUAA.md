# Como Testar a Autenticação JWT do XSUAA

## 1. Obter um Token JWT do XSUAA

Para obter um token JWT válido do XSUAA, você precisa fazer uma requisição OAuth2 Client Credentials Flow:

### Requisição cURL para obter token:

```bash
curl --location --request POST 'https://cerc-financeintegrator-1ciub5oj.authentication.br10.hana.ondemand.com/oauth/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=sb-revvo!t8564' \
--data-urlencode 'client_secret=e0ffc4ed-9e40-418c-b1e2-aba1164c467a$7xWdMtHxEcK3w--0KJCNnnF36JqkgOjIR2XoITKoLyI='
```

### Resposta esperada:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleS1pZC0xIn0.eyJzdWIiOiJzYi1yZXZ2byF0ODU2NCIsInVzZXJfbmFtZSI6InJldnZvIXQ4NTY0Iiwic2NvcGUiOlsiQURNSU4iLCJVU0VSIl0sImV4cCI6MTczNDI4ODAwMCwiaWF0IjoxNzM0Mjg0NDAwfQ...",
  "token_type": "bearer",
  "expires_in": 3600,
  "scope": "ADMIN USER",
  "jti": "unique-token-id"
}
```

## 2. Testar com a sua aplicação

### A. Endpoint de teste básico:

```bash
curl --location 'http://localhost:8081/sap/jwt/test' \
--header 'Authorization: Bearer SEU_TOKEN_AQUI'
```

### B. Verificar status da autenticação:

```bash
curl --location 'http://localhost:8081/sap/jwt/status' \
--header 'Authorization: Bearer SEU_TOKEN_AQUI'
```

### C. Testar endpoint protegido:

```bash
curl --location 'http://localhost:8081/sap/jwt/protected' \
--header 'Authorization: Bearer SEU_TOKEN_AQUI'
```

## 3. O que o Spring Boot faz automaticamente

Quando você envia uma requisição com `Authorization: Bearer <token>`:

1. **Validação da assinatura**: O Spring busca as chaves públicas em:
   - `https://cerc-financeintegrator-1ciub5oj.authentication.br10.hana.ondemand.com/token_keys`

2. **Validação do issuer**: Verifica se o token foi emitido por:
   - `https://cerc-financeintegrator-1ciub5oj.authentication.br10.hana.ondemand.com/oauth/token`

3. **Extração de roles**: O Spring lê o claim `scope` do JWT e converte para authorities:
   - Se o JWT tem `"scope": "ADMIN USER FINANCE_MANAGER"`
   - O Spring cria authorities: `SCOPE_ADMIN`, `SCOPE_USER`, `SCOPE_FINANCE_MANAGER`

4. **Criação do Authentication**: O Spring cria um objeto `JwtAuthenticationToken` com:
   - Username: extraído do claim `user_name` ou `sub`
   - Authorities: extraídas do claim `scope`

## 4. Como funcionam os claims do JWT XSUAA

Um token JWT do XSUAA tem esta estrutura (payload):

```json
{
  "sub": "sb-revvo!t8564",
  "user_name": "adriana.amaral@partner.ideen.tech",
  "email": "adriana.amaral@partner.ideen.tech",
  "given_name": "Adriana",
  "family_name": "Amaral",
  "scope": ["ADMIN", "USER", "FINANCE_MANAGER"],
  "client_id": "sb-revvo!t8564",
  "grant_type": "client_credentials",
  "zid": "693db0bf-0aa6-4242-a2ab-96b0204c0b35",
  "iss": "https://cerc-financeintegrator-1ciub5oj.authentication.br10.hana.ondemand.com/oauth/token",
  "exp": 1734288000,
  "iat": 1734284400
}
```

## 5. Diferença entre autenticação via Headers vs JWT

### Via Headers (mock/desenvolvimento):
- Você envia `X-Authenticated-User`, `X-SAP-ROLES`, etc.
- O `SapSsoFilter` lê esses headers e cria a autenticação
- **NÃO É SEGURO** - qualquer um pode falsificar os headers
- Usado apenas para desenvolvimento/testes

### Via JWT (produção/real):
- Você envia `Authorization: Bearer <token_jwt>`
- O Spring valida a assinatura criptográfica do token
- **SEGURO** - o token só pode ser criado pelo XSUAA com a chave privada
- As roles vêm do claim `scope` dentro do token assinado
- Usado em produção quando o SAP Build Work Zone chama sua aplicação

## 6. Como o SAP Build Work Zone envia o token

Quando sua aplicação roda no SAP Build Work Zone (Fiori Launchpad):

1. O usuário faz login no Work Zone
2. O Work Zone obtém um token JWT do XSUAA para esse usuário
3. Quando o Work Zone chama sua aplicação, ele envia:
   - Header: `Authorization: Bearer <token_jwt_do_usuario>`
   - O token já contém todas as roles/scopes do usuário autenticado

4. Sua aplicação recebe o token e o Spring valida automaticamente
5. As roles do usuário ficam disponíveis em `authentication.getAuthorities()`

## 7. Próximos passos

Para que sua aplicação funcione de verdade no SAP Build Work Zone:

1. **Deploy da aplicação**: Fazer deploy no SAP BTP (Cloud Foundry ou Kyma)

2. **Binding com XSUAA**: No manifest.yml ou deployment, fazer binding com a instância XSUAA:
   ```yaml
   services:
     - revvo-autenticacao-sso-sap
   ```

3. **Configurar no Work Zone**: 
   - Criar um Content Provider apontando para sua aplicação
   - Configurar as roles necessárias
   - Criar tiles que apontam para suas rotas

4. **Configurar roles no xs-security.json**: Definir os scopes/roles que sua aplicação usa:
   ```json
   {
     "xsappname": "revvo",
     "scopes": [
       {
         "name": "$XSAPPNAME.Admin",
         "description": "Administrador"
       },
       {
         "name": "$XSAPPNAME.User",
         "description": "Usuário"
       }
     ],
     "role-templates": [
       {
         "name": "Admin",
         "scope-references": ["$XSAPPNAME.Admin"]
       },
       {
         "name": "User",
         "scope-references": ["$XSAPPNAME.User"]
       }
     ]
   }
   ```

## 8. Verificação rápida

Para saber se **extractedRoles** veio do cURL ou da autenticação real:

- **Veio do cURL**: Se você enviou header `X-SAP-ROLES: ADMIN,USER,FINANCE_MANAGER`
- **Veio da autenticação real**: Se você enviou `Authorization: Bearer <token>` e o token tem essas roles no claim `scope`

Para ter certeza, veja o campo `jwtClaims` na resposta:
- Se for `"jwtClaims": "No JWT found"` → veio dos headers mockados
- Se tiver os claims do JWT → veio da autenticação real via token


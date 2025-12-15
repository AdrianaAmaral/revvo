# Integração SSO com SAP Build Work Zone / Fiori Launchpad

## 📋 Sumário
- [Visão Geral](#visão-geral)
- [Como Funciona](#como-funciona)
- [Configuração no SAP Build Work Zone](#configuração-no-sap-build-work-zone)
- [Configuração da Aplicação](#configuração-da-aplicação)
- [Testando Localmente](#testando-localmente)
- [Deploy e Validação](#deploy-e-validação)
- [Troubleshooting](#troubleshooting)

---

## 🎯 Visão Geral

A aplicação agora suporta autenticação SSO (Single Sign-On) quando executada dentro do **SAP Build Work Zone** (antigo SAP Launchpad). 

### Fluxo de Autenticação

```
┌─────────────────┐
│  Usuário SAP    │
│  (já logado)    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│ SAP Build Work Zone     │
│ (Launchpad/Fiori)       │
│                         │
│ - Captura usuário       │
│ - Captura roles/groups  │
│ - Adiciona headers/JWT  │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Approuter (BTP)         │
│ - X-Authenticated-User  │
│ - X-SAP-ROLES           │
│ - JWT token             │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Sua Aplicação Java      │
│                         │
│ SapSsoFilter            │
│  ├─ Extrai usuário      │
│  ├─ Extrai roles SAP    │
│  ├─ Mapeia para Revvo   │
│  ├─ Cacheia permissões  │
│  └─ Autentica no Spring │
└─────────────────────────┘
```

---

## ⚙️ Como Funciona

### 1. **SapSsoFilter** (Filtro Principal)
- Intercepta todas as requisições HTTP
- Extrai usuário e roles dos headers ou JWT token
- Cria autenticação no Spring Security automaticamente
- **Não há tela de login** quando executado via Fiori

### 2. **SapContextExtractor** (Extração de Dados)
Tenta extrair informações do usuário na seguinte ordem:

#### Para **Username**:
1. Headers: `X-SAP-USER`, `X-Authenticated-User`, `X-User`, `X-Forwarded-User`
2. JWT Claims: `preferred_username`, `user_name`, `email`, `sub`

#### Para **Roles SAP**:
1. Headers: `X-SAP-ROLES`, `X-SAP-GROUPS`, `X-User-Roles`, `X-Groups`
2. JWT Claims: `groups`, `roles`, `authorities`, `scope`

### 3. **SapSsoCache** (Cache de Permissões)
- Mantém permissões em memória por 15 minutos (configurável)
- Evita processar mapeamento SAP→Revvo a cada request
- **Produção**: considere usar Redis para cache distribuído

### 4. **PermissionService** (Lógica de Negócio)
- Mapeia roles SAP para roles Revvo (via `sap-revvo.json`)
- Aplica roles no Keycloak (integração futura)
- Retorna objeto `UserPermissions` completo

---

## 🔧 Configuração no SAP Build Work Zone

### Passo 1: Criar HTML5 App no BTP

1. **Acesse o BTP Cockpit** → Seu Subaccount
2. **Navegue até**: Services → Instances and Subscriptions
3. **Crie um Destination** apontando para sua aplicação Java:

```properties
Name: revvo-backend
Type: HTTP
URL: https://sua-app.cfapps.br10.hana.ondemand.com
ProxyType: Internet
Authentication: NoAuthentication
# Ou use OAuth2SAMLBearerAssertion se precisar propagar token
```

### Passo 2: Criar App Descriptor (manifest.json)

Dentro do seu app SAPUI5/HTML5:

```json
{
  "sap.app": {
    "id": "com.revvo.app",
    "type": "application",
    "title": "Revvo Finance",
    "dataSources": {
      "revvoBackend": {
        "uri": "/sap/",
        "type": "OData",
        "settings": {
          "odataVersion": "2.0"
        }
      }
    }
  },
  "sap.ui5": {
    "routing": {
      "config": {
        "routerClass": "sap.m.routing.Router",
        "async": true
      }
    }
  },
  "sap.cloud": {
    "service": "com.revvo.service"
  }
}
```

### Passo 3: Configurar xs-app.json (Approuter)

Se usar Approuter no BTP:

```json
{
  "welcomeFile": "/index.html",
  "authenticationMethod": "route",
  "routes": [
    {
      "source": "^/sap/(.*)$",
      "target": "$1",
      "destination": "revvo-backend",
      "authenticationType": "xsuaa",
      "csrfProtection": false
    },
    {
      "source": "^(.*)$",
      "target": "$1",
      "localDir": ".",
      "authenticationType": "xsuaa"
    }
  ]
}
```

### Passo 4: Configurar XSUAA (OAuth)

No `xs-security.json`:

```json
{
  "xsappname": "revvo-app",
  "tenant-mode": "dedicated",
  "scopes": [],
  "role-templates": [],
  "role-collections": [
    {
      "name": "RevvoUser",
      "description": "Usuário básico do Revvo",
      "role-template-references": []
    }
  ],
  "oauth2-configuration": {
    "redirect-uris": [
      "https://*.cfapps.br10.hana.ondemand.com/**"
    ]
  }
}
```

### Passo 5: Adicionar Tile no Work Zone

1. **Content Manager** → Create → App
2. **Configurar**:
   - **Title**: Revvo Finance
   - **Open App**: In Place
   - **URL**: `/sap/login-sso` (endpoint da sua app Java)
   - **System**: revvo-backend (destination criada)

3. **Adicionar ao Catalogo e Group**
4. **Publicar** as mudanças

---

## 🖥️ Configuração da Aplicação

### application.yaml

Adicione configurações opcionais:

```yaml
spring:
  application:
    name: revvo
  security:
    # Configurações de SSO
    sap:
      sso:
        enabled: true
        cache-ttl-minutes: 15
        # Headers esperados (documentação)
        username-headers:
          - X-SAP-USER
          - X-Authenticated-User
          - X-User
        roles-headers:
          - X-SAP-ROLES
          - X-SAP-GROUPS

server:
  port: 8081

# Logs para debug
logging:
  level:
    com.revvo.security: DEBUG
    com.revvo.sap: DEBUG
```

### Endpoints Disponíveis

| Endpoint | Método | Descrição |
|----------|--------|-----------|
| `/sap/login-sso` | GET | Autenticação SSO (chamado pelo Fiori) |
| `/sap/login-mock` | POST | Mock para testes locais |
| `/sap/debug-context` | GET | Debug: mostra headers e JWT recebidos |

---

## 🧪 Testando Localmente

### 1. Simular Headers SAP

Use Postman ou curl:

```bash
# Teste com headers
curl -X GET http://localhost:8081/sap/login-sso \
  -H "X-Authenticated-User: USUARIO_TESTE" \
  -H "X-SAP-ROLES: Z_ROLE_FINANCEIRO,Z_ROLE_CONSULTA"

# Resultado esperado:
{
  "username": "USUARIO_TESTE",
  "sapRoles": ["Z_ROLE_FINANCEIRO", "Z_ROLE_CONSULTA"],
  "revvoRoles": ["FINANCEIRO", "CONSULTA"]
}
```

### 2. Testar com Mock (sem headers)

```bash
curl -X POST "http://localhost:8081/sap/login-mock?username=TESTE&sapRoles=Z_ADMIN,Z_USER"

# Resultado:
{
  "username": "TESTE",
  "sapRoles": ["Z_ADMIN", "Z_USER"],
  "revvoRoles": ["ADMIN", "USER"]
}
```

### 3. Debug Context

```bash
curl http://localhost:8081/sap/debug-context \
  -H "X-SAP-USER: TESTE" \
  -H "Authorization: Bearer eyJhbGc..."

# Mostra:
{
  "username": "TESTE",
  "sapRoles": [...],
  "receivedHeaders": {
    "x-sap-user": "TESTE",
    "authorization": "Bearer eyJ..."
  },
  "jwtClaims": {
    "sub": "...",
    "groups": [...]
  }
}
```

---

## 🚀 Deploy e Validação

### 1. Build e Deploy

```bash
# Build local
mvn clean package

# Deploy no Cloud Foundry (BTP)
cf push revvo-app -p target/revvo-0.0.1-SNAPSHOT.jar
```

### 2. Validar no Work Zone

1. **Acesse** o Work Zone
2. **Clique** no tile do Revvo
3. **Não deve aparecer** tela de login
4. **Deve carregar** automaticamente com seu usuário SAP

### 3. Verificar Logs

```bash
cf logs revvo-app --recent | grep "SSO"

# Procure por:
# "SAP SSO detectado para usuário: XXX"
# "Autenticação SSO bem-sucedida para usuário: XXX com Y roles Revvo"
```

### 4. Validar Roles

No frontend, chame o endpoint:

```javascript
fetch('/sap/login-sso')
  .then(r => r.json())
  .then(data => {
    console.log('User:', data.username);
    console.log('SAP Roles:', data.sapRoles);
    console.log('Revvo Roles:', data.revvoRoles);
  });
```

---

## 🔍 Troubleshooting

### ❌ Problema: "Usuário não autenticado"

**Possível causa**: Headers não estão chegando

**Solução**:
1. Use `/sap/debug-context` para ver headers recebidos
2. Verifique configuração do Approuter/Destination
3. Confirme que `authenticationType: xsuaa` está configurado

### ❌ Problema: Roles SAP vazias

**Possível causa**: Roles não estão sendo propagadas

**Solução**:
1. Verifique se o usuário tem roles atribuídas no SAP
2. Configure `xs-security.json` para incluir `groups` no token
3. Adicione no `xs-security.json`:

```json
{
  "oauth2-configuration": {
    "token-validity": 3600,
    "grant-types": ["authorization_code"],
    "system-attributes": ["groups", "rolecollections"]
  }
}
```

### ❌ Problema: Cache não expira

**Solução**: Limpar cache manualmente ou reiniciar app

```java
// Endpoint para limpar cache (adicione com segurança admin)
@DeleteMapping("/sap/cache/clear")
public ResponseEntity<Void> clearCache() {
    sapSsoCache.clear();
    return ResponseEntity.ok().build();
}
```

### ❌ Problema: CORS errors

**Solução**: Adicione configuração CORS:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/sap/**")
                .allowedOrigins("https://*.cfapps.br10.hana.ondemand.com")
                .allowedMethods("GET", "POST")
                .allowCredentials(true);
    }
}
```

---

## 📊 Monitoramento

### Métricas Importantes

1. **Taxa de autenticação SSO bem-sucedida**
2. **Hit rate do cache de permissões**
3. **Tempo de resposta do mapeamento SAP→Revvo**

### Logs Estruturados

Adicione ao `application.yaml`:

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  level:
    com.revvo.security.SapSsoFilter: INFO
    com.revvo.sap.SapContextExtractor: DEBUG
```

---

## 📚 Referências

- [SAP Build Work Zone Documentation](https://help.sap.com/docs/build-work-zone-standard-edition)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Cloud Foundry Authentication](https://docs.cloudfoundry.org/concepts/architecture/uaa.html)

---

## ✅ Checklist de Implementação

- [x] Filtro SSO criado (`SapSsoFilter`)
- [x] Extrator de contexto SAP implementado (`SapContextExtractor`)
- [x] Cache de permissões implementado (`SapSsoCache`)
- [x] Configuração Spring Security
- [x] Endpoint de debug (`/sap/debug-context`)
- [ ] Configurar app no SAP Build Work Zone
- [ ] Criar destination no BTP
- [ ] Configurar xs-app.json e xs-security.json
- [ ] Deploy da aplicação
- [ ] Testar autenticação no Work Zone
- [ ] Validar propagação de roles
- [ ] Configurar monitoramento

---

**Última atualização**: 2025-12-15


// Adicionar tratamento de erros
process.on('unhandledRejection', (reason, promise) => {
    console.error('Unhandled Rejection at:', promise, 'reason:', reason);
    process.exit(1);
});

process.on('uncaughtException', (error) => {
    console.error('Uncaught Exception:', error);
    process.exit(1);
});

console.log('Iniciando approuter...');
console.log('Diretório atual:', __dirname);

// Configurar destinations como variável de ambiente
process.env.destinations = JSON.stringify([
    {
        "name": "revvo-backend",
        "url": "http://localhost:8081",
        "forwardAuthToken": true
    }
]);

try {
    // Iniciar o approuter
    const approuter = require('@sap/approuter')();
    console.log('Approuter criado com sucesso');

    approuter.start({
        port: process.env.PORT || 5000
    });

    console.log('Approuter iniciado na porta', process.env.PORT || 5000);
} catch (error) {
    console.error('Erro ao iniciar approuter:', error);
    process.exit(1);
}

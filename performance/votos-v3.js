import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const quantidadeVus = Number(__ENV.VUS || 50);
const duracao = __ENV.DURATION || '1m';
const quantidadeCpfsSeed = Number(__ENV.CPFS_SEED || 100000);
const cpfNaoEncontradoACada = Number(__ENV.CPF_NAO_ENCONTRADO_A_CADA || 20);
const votosRecebidos = new Rate('votos_recebidos');

if (!Number.isInteger(quantidadeCpfsSeed) || quantidadeCpfsSeed < 2)
    throw new Error('CPFS_SEED deve ser um inteiro maior que 1.');
if (!Number.isInteger(cpfNaoEncontradoACada) || cpfNaoEncontradoACada < 2)
    throw new Error('CPF_NAO_ENCONTRADO_A_CADA deve ser um inteiro maior que 1.');

export const options = {
    setupTimeout: '2m',
    discardResponseBodies: true,
    systemTags: ['status', 'method', 'name', 'check', 'error', 'error_code', 'expected_response', 'scenario'],
    scenarios: {
        // Mantém os usuários ativos durante todo o período para medir a capacidade de absorção da API.
        absorcao: {
            executor: 'constant-vus',
            vus: quantidadeVus,
            duration: duracao,
            gracefulStop: '30s'
        }
    },
    thresholds: {
        votos_recebidos: ['rate>0.99'],
        'checks{operacao:voto}': ['rate>0.99'],
        'http_req_failed{operacao:voto}': ['rate<0.01'],
        'http_req_duration{operacao:voto}': ['p(95)<500']
    }
};

// Cria e abre uma pauta antes da carga para que as métricas principais representem apenas os votos.
export function setup() {
    const identificador = Date.now();
    const pauta = http.post(
        `${baseUrl}/api/v1/pautas`,
        JSON.stringify({
            titulo: `Performance ${identificador}`,
            descricao: 'Pauta criada pelo teste de absorção da votação v3'
        }),
        {
            headers: { 'Content-Type': 'application/json' },
            responseType: 'text',
            tags: { name: 'POST /api/v1/pautas', operacao: 'preparacao' }
        }
    );
    if (pauta.status !== 201)
        fail(`Não foi possível criar a pauta: HTTP ${pauta.status}`);

    const pautaId = pauta.json('id');
    const abertura = http.post(
        `${baseUrl}/api/v1/pautas/${pautaId}/open`,
        JSON.stringify({ dataEncerramento: dataEncerramento().toISOString() }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { name: 'POST /api/v1/pautas/:id/open', operacao: 'preparacao' }
        }
    );
    if (abertura.status !== 200)
        fail(`Não foi possível abrir a pauta ${pautaId}: HTTP ${abertura.status}`);

    return { pautaId };
}

// Cada iteração envia um voto novo, alternando entre SIM e NÃO.
export default function(data) {
    const sequencial = exec.scenario.iterationInTest + 1;
    if (sequencial > 999999999)
        fail('O teste excedeu a quantidade de CPFs únicos suportada.');

    const response = http.post(
        `${baseUrl}/api/v3/votos`,
        JSON.stringify({
            cpf: selecionarCpf(sequencial),
            opcao: sequencial % 2 === 0 ? 'SIM' : 'NAO',
            pautaId: data.pautaId
        }),
        {
            headers: { 'Content-Type': 'application/json' },
            responseType: 'none',
            tags: { name: 'POST /api/v3/votos', operacao: 'voto' }
        }
    );

    const recebido = check(
        response,
        { 'voto recebido': resultado => resultado.status === 201 },
        { operacao: 'voto' }
    );
    votosRecebidos.add(recebido);
}

// A maior parte usa o seed, mas uma pequena parcela fica de fora para cobrir o cenário de não encontrado.
function selecionarCpf(sequencial) {
    if (sequencial % cpfNaoEncontradoACada === 0)
        return gerarCpf(quantidadeCpfsSeed + sequencial);

    const ordemNoSeed = sequencial - Math.floor(sequencial / cpfNaoEncontradoACada);
    if (ordemNoSeed > quantidadeCpfsSeed)
        return gerarCpf(quantidadeCpfsSeed + sequencial);
    if (ordemNoSeed === quantidadeCpfsSeed)
        return '03425110250';
    return gerarCpf(ordemNoSeed);
}

// Deixa a pauta aberta com folga durante toda a execução, inclusive em testes mais longos.
function dataEncerramento() {
    const margemMs = duracaoEmSegundos(duracao) * 1000 + 60 * 60 * 1000;
    return new Date(Date.now() + Math.max(margemMs, 2 * 60 * 60 * 1000));
}

// Gera CPFs válidos a partir de um número sequencial, evitando colisões dentro da mesma pauta.
function gerarCpf(sequencial) {
    const base = String(sequencial).padStart(9, '0');
    const primeiroDigito = calcularDigito(base);
    const segundoDigito = calcularDigito(base + primeiroDigito);
    return base + primeiroDigito + segundoDigito;
}

function calcularDigito(cpf) {
    let soma = 0;
    let peso = cpf.length + 1;
    for (const numero of cpf)
        soma += Number(numero) * peso--;
    const digito = 11 - soma % 11;
    return digito >= 10 ? 0 : digito;
}

function duracaoEmSegundos(valor) {
    const correspondencia = /^(\d+(?:\.\d+)?)(ms|s|m|h)$/.exec(valor);
    if (!correspondencia)
        throw new Error(`Duração inválida: ${valor}`);
    const quantidade = Number(correspondencia[1]);
    const unidade = correspondencia[2];
    if (unidade === 'ms') return quantidade / 1000;
    if (unidade === 's') return quantidade;
    if (unidade === 'm') return quantidade * 60;
    return quantidade * 3600;
}

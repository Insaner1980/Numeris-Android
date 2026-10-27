import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const deepsecRequire = createRequire(require.resolve('deepsec'));
const sandboxDirectory = dirname(deepsecRequire.resolve('@vercel/sandbox'));

// Backport of https://github.com/vercel/sandbox/pull/219 for Deepsec's v1 SDK.
for (const extension of ['js', 'cjs']) {
  const { NetworkPolicyValidator } = await import(
    pathToFileURL(join(sandboxDirectory, 'api-client', `validators.${extension}`))
  );
  const { Sandbox } = await import(pathToFileURL(join(sandboxDirectory, `index.${extension}`)));
  const policy = {
    mode: 'custom',
    allowedDomains: ['example.test'],
    injectionRules: [{ domain: 'example.test', headers: { 'x-test': 'fixture' } }],
  };

  test(`${extension}: Zod 4 accepts string headers and rejects invalid headers`, () => {
    assert.deepEqual(NetworkPolicyValidator.parse(policy), policy);
    const invalid = {
      ...policy,
      injectionRules: [{ domain: 'example.test', headers: { 'x-test': 123 } }],
    };
    assert.equal(NetworkPolicyValidator.safeParse(invalid).success, false);
  });

  test(`${extension}: create and get preserve Deepsec's sandboxId API`, async () => {
    const requests = [];
    const sandbox = {
      id: 'sbx_fixture', memory: 2048, vcpus: 1, region: 'iad1',
      runtime: 'node24', timeout: 60000, status: 'running',
      requestedAt: 1, createdAt: 1, updatedAt: 1, cwd: '/vercel/sandbox',
      networkPolicy: policy,
    };
    const options = {
      token: 'test-only-token', teamId: 'team_fixture', projectId: 'prj_fixture',
      fetch: async (url, init) => {
        requests.push({ url, init });
        return Response.json({ sandbox, routes: [] });
      },
    };
    const created = await Sandbox.create({ ...options, runtime: 'node24', timeout: 60000 });
    assert.equal(created.sandboxId, sandbox.id);
    assert.equal(JSON.parse(requests[0].init.body).runtime, 'node24');
    const restored = await Sandbox.get({ ...options, sandboxId: created.sandboxId });
    assert.equal(restored.sandboxId, sandbox.id);
    assert.ok(requests[1].url.includes(sandbox.id));
    for (const method of ['writeFiles', 'runCommand', 'getCommand', 'snapshot', 'downloadFile', 'stop']) {
      assert.equal(typeof restored[method], 'function', method);
    }
  });
}

/* SPDX-License-Identifier: AGPL-3.0-only */
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  SURFACE_CONTRACT_KINDS,
  isSurfaceContract,
  validateSurfaceContract,
  SURFACE_CONTRACT_FIXTURES,
  validateSurfaceFixture,
} from '../src/index.mjs';

test('SURFACE_CONTRACT_KINDS lists all expected surface kinds', () => {
  const expected = [
    'home',
    'project-work',
    'project-files',
    'project-activity',
    'project-agents',
    'models',
    'automation',
    'history',
    'settings',
    'developer-specgraph',
  ];
  assert.deepEqual(SURFACE_CONTRACT_KINDS, expected);
});

test('isSurfaceContract accepts a valid contract', () => {
  const contract = {
    surfaceId: 'home',
    requiredRoutes: ['/v1/answers', '/v1/projects'],
    components: [
      { componentId: 'EngineSixAnswers', requiredData: { answers: 'object', queue: 'object' } },
    ],
  };
  assert.equal(isSurfaceContract(contract), true);
});

test('isSurfaceContract rejects invalid surfaceId', () => {
  const contract = {
    surfaceId: 'not-a-real-surface',
    requiredRoutes: ['/v1/answers'],
    components: [{ componentId: 'Test', requiredData: {} }],
  };
  assert.equal(isSurfaceContract(contract), false);
});

test('isSurfaceContract rejects missing requiredRoutes', () => {
  const contract = {
    surfaceId: 'home',
    components: [{ componentId: 'Test', requiredData: {} }],
  };
  assert.equal(isSurfaceContract(contract), false);
});

test('isSurfaceContract rejects non-array requiredRoutes', () => {
  const contract = {
    surfaceId: 'home',
    requiredRoutes: '/v1/answers',
    components: [{ componentId: 'Test', requiredData: {} }],
  };
  assert.equal(isSurfaceContract(contract), false);
});

test('isSurfaceContract rejects non-/v1/ routes', () => {
  const contract = {
    surfaceId: 'home',
    requiredRoutes: ['/api/answers'],
    components: [{ componentId: 'Test', requiredData: {} }],
  };
  assert.equal(isSurfaceContract(contract), false);
});

test('isSurfaceContract rejects missing components', () => {
  const contract = {
    surfaceId: 'home',
    requiredRoutes: ['/v1/answers'],
  };
  assert.equal(isSurfaceContract(contract), false);
});

test('isSurfaceContract rejects invalid component shape', () => {
  const contract = {
    surfaceId: 'home',
    requiredRoutes: ['/v1/answers'],
    components: [{ componentId: 'Test' }],
  };
  assert.equal(isSurfaceContract(contract), false);
});

test('SURFACE_CONTRACT_FIXTURES has all expected surface kinds', () => {
  const fixtureKinds = Object.keys(SURFACE_CONTRACT_FIXTURES).sort();
  const expected = SURFACE_CONTRACT_KINDS.slice().sort();
  assert.deepEqual(fixtureKinds, expected);
});

test('each fixture passes isSurfaceContract', () => {
  for (const [surfaceId, fixture] of Object.entries(SURFACE_CONTRACT_FIXTURES)) {
    assert.equal(isSurfaceContract(fixture), true, `fixture ${surfaceId} should pass isSurfaceContract`);
  }
});

test('each fixture has requiredRoutes as array of /v1/ paths', () => {
  for (const [surfaceId, fixture] of Object.entries(SURFACE_CONTRACT_FIXTURES)) {
    for (const route of fixture.requiredRoutes) {
      assert.match(route, /^\/v1\//, `fixture ${surfaceId} route ${route} must start with /v1/`);
    }
  }
});

test('each fixture has components with componentId and requiredData', () => {
  for (const [surfaceId, fixture] of Object.entries(SURFACE_CONTRACT_FIXTURES)) {
    for (const component of fixture.components) {
      assert.ok(component.componentId && typeof component.componentId === 'string', `fixture ${surfaceId} component missing componentId`);
      assert.ok(component.requiredData && typeof component.requiredData === 'object', `fixture ${surfaceId} component ${component.componentId} missing requiredData`);
    }
  }
});

test('validateSurfaceContract accepts instance matching fixture', () => {
  const fixture = SURFACE_CONTRACT_FIXTURES.home;
  const instance = {
    surfaceId: 'home',
    '/v1/answers': { answers: { objective: { value: 'test', health: 'verified', signal: 'ok' } }, queue: { readable: true } },
    '/v1/projects': { projects: [] },
    '/v1/approvals': { pending: [] },
    '/v1/quota': { used: 0, limit: 100, remaining: 100, fractionUsed: 0, resetAt: null },
    EngineSixAnswers: {},
    SessionList: {},
    QuotaChips: {},
    ProjectCard: {},
  };
  const result = validateSurfaceContract(fixture, instance);
  assert.equal(result.ok, true, `validation should pass for valid instance: ${JSON.stringify(result)}`);
});

test('validateSurfaceContract rejects instance with wrong surfaceId', () => {
  const fixture = SURFACE_CONTRACT_FIXTURES.home;
  const instance = {
    surfaceId: 'project-work',
    '/v1/answers': { answers: {}, queue: { readable: true } },
    '/v1/projects': { projects: [] },
    '/v1/approvals': { pending: [] },
    '/v1/quota': { used: 0, limit: 100, remaining: 100, fractionUsed: 0, resetAt: null },
  };
  const result = validateSurfaceContract(fixture, instance);
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'surface-id-mismatch');
});

test('validateSurfaceContract rejects instance missing required route data', () => {
  const fixture = SURFACE_CONTRACT_FIXTURES.home;
  const instance = {
    surfaceId: 'home',
    '/v1/projects': { projects: [] },
    '/v1/approvals': { pending: [] },
    '/v1/quota': { used: 0, limit: 100, remaining: 100, fractionUsed: 0, resetAt: null },
  };
  const result = validateSurfaceContract(fixture, instance);
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'missing-route-data');
});

test('validateSurfaceContract rejects instance missing required component', () => {
  const fixture = SURFACE_CONTRACT_FIXTURES.home;
  const instance = {
    surfaceId: 'home',
    '/v1/answers': { answers: {}, queue: { readable: true } },
    '/v1/projects': { projects: [] },
    '/v1/approvals': { pending: [] },
    '/v1/quota': { used: 0, limit: 100, remaining: 100, fractionUsed: 0, resetAt: null },
    // Missing EngineSixAnswers component
  };
  const result = validateSurfaceContract(fixture, instance);
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'missing-component');
});

test('validateSurfaceFixture delegates to validateSurfaceContract', () => {
  const instance = {
    surfaceId: 'home',
    '/v1/answers': { answers: {}, queue: { readable: true } },
    '/v1/projects': { projects: [] },
    '/v1/approvals': { pending: [] },
    '/v1/quota': { used: 0, limit: 100, remaining: 100, fractionUsed: 0, resetAt: null },
    EngineSixAnswers: {},
    SessionList: {},
    QuotaChips: {},
    ProjectCard: {},
  };
  const result = validateSurfaceFixture('home', instance);
  assert.equal(result.ok, true);
});

test('validateSurfaceFixture rejects unknown surfaceId', () => {
  const instance = { surfaceId: 'unknown' };
  const result = validateSurfaceFixture('unknown', instance);
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'unknown-fixture');
});

test('validateSurfaceContract rejects invalid contract', () => {
  const badContract = { surfaceId: 'home' };
  const instance = { surfaceId: 'home' };
  const result = validateSurfaceContract(badContract, instance);
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'invalid-contract');
});

test('validateSurfaceContract rejects non-object instance', () => {
  const fixture = SURFACE_CONTRACT_FIXTURES.home;
  const result = validateSurfaceContract(fixture, 'not an object');
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'invalid-instance');
});
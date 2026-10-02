import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Request, Response, NextFunction } from 'express';
import { validate } from '../../src/middleware/validate';
import { z } from 'zod';

function createMockReqResNext(body = {}, query = {}, params = {}) {
  const req = { body, query, params } as Request;
  const res = {
    status: vi.fn().mockReturnThis(),
    json: vi.fn().mockReturnThis(),
  } as unknown as Response;
  const next = vi.fn() as NextFunction;
  return { req, res, next };
}

describe('validate middleware', () => {
  describe('body validation', () => {
    const schema = z.object({
      name: z.string().min(1),
      age: z.number().int().positive(),
    });

    it('calls next() when body is valid', () => {
      const { req, res, next } = createMockReqResNext({ name: 'Test', age: 25 });
      validate({ body: schema })(req, res, next);

      expect(next).toHaveBeenCalled();
      expect(res.status).not.toHaveBeenCalled();
    });

    it('returns 422 when body is invalid', () => {
      const { req, res, next } = createMockReqResNext({ name: '', age: -1 });
      validate({ body: schema })(req, res, next);

      expect(next).not.toHaveBeenCalled();
      expect(res.status).toHaveBeenCalledWith(422);
      expect(res.json).toHaveBeenCalledWith(
        expect.objectContaining({
          success: false,
          error: expect.objectContaining({
            code: 'VALIDATION_ERROR',
          }),
        }),
      );
    });
  });

  describe('query validation', () => {
    const schema = z.object({
      page: z.coerce.number().int().positive(),
    });

    it('calls next() when query is valid', () => {
      const { req, res, next } = createMockReqResNext({}, { page: '1' });
      validate({ query: schema })(req, res, next);
      expect(next).toHaveBeenCalled();
    });

    it('returns 422 when query is invalid', () => {
      const { req, res, next } = createMockReqResNext({}, { page: 'abc' });
      validate({ query: schema })(req, res, next);
      expect(next).not.toHaveBeenCalled();
      expect(res.status).toHaveBeenCalledWith(422);
    });
  });

  describe('params validation', () => {
    const schema = z.object({
      id: z.string().min(1),
    });

    it('calls next() when params are valid', () => {
      const { req, res, next } = createMockReqResNext({}, {}, { id: 'abc123' });
      validate({ params: schema })(req, res, next);
      expect(next).toHaveBeenCalled();
    });
  });

  describe('combined validation', () => {
    it('validates body, query, and params together', () => {
      const bodySchema = z.object({ name: z.string() });
      const querySchema = z.object({ page: z.coerce.number() });
      const paramsSchema = z.object({ id: z.string() });

      const { req, res, next } = createMockReqResNext(
        { name: 'Test' },
        { page: '1' },
        { id: 'abc' },
      );
      validate({ body: bodySchema, query: querySchema, params: paramsSchema })(req, res, next);
      expect(next).toHaveBeenCalled();
    });
  });
});

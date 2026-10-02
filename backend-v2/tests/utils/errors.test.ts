import { describe, it, expect } from 'vitest';
import {
  AppError,
  NotFoundError,
  BadRequestError,
  UnauthorizedError,
  ForbiddenError,
  ConflictError,
  ValidationError,
  TooManyRequestsError,
  InternalError,
} from '../../src/utils/errors';

describe('AppError', () => {
  it('stores statusCode, code, message, and isOperational', () => {
    const error = new AppError('test error', 418, 'TEAPOT', true);

    expect(error).toBeInstanceOf(Error);
    expect(error).toBeInstanceOf(AppError);
    expect(error.message).toBe('test error');
    expect(error.statusCode).toBe(418);
    expect(error.code).toBe('TEAPOT');
    expect(error.isOperational).toBe(true);
  });

  it('defaults isOperational to true', () => {
    const error = new AppError('test', 400, 'BAD');
    expect(error.isOperational).toBe(true);
  });

  it('has a stack trace', () => {
    const error = new AppError('test', 500, 'FAIL');
    expect(error.stack).toBeDefined();
  });
});

describe('Error subclasses', () => {
  const cases: [string, Error, number, string][] = [
    ['NotFoundError', new NotFoundError(), 404, 'NOT_FOUND'],
    ['NotFoundError (custom)', new NotFoundError('User'), 404, 'NOT_FOUND'],
    ['BadRequestError', new BadRequestError(), 400, 'BAD_REQUEST'],
    ['UnauthorizedError', new UnauthorizedError(), 401, 'UNAUTHORIZED'],
    ['ForbiddenError', new ForbiddenError(), 403, 'FORBIDDEN'],
    ['ConflictError', new ConflictError(), 409, 'CONFLICT'],
    ['ValidationError', new ValidationError(), 422, 'VALIDATION_ERROR'],
    ['TooManyRequestsError', new TooManyRequestsError(), 429, 'TOO_MANY_REQUESTS'],
    ['InternalError', new InternalError(), 500, 'INTERNAL_ERROR'],
  ];

  it.each(cases)('%s has correct statusCode and code', (_name, error, statusCode, code) => {
    expect(error).toBeInstanceOf(AppError);
    expect((error as AppError).statusCode).toBe(statusCode);
    expect((error as AppError).code).toBe(code);
  });

  it('NotFoundError with resource name includes it in the message', () => {
    const error = new NotFoundError('User');
    expect(error.message).toBe('User not found');
  });

  it('InternalError is not operational', () => {
    const error = new InternalError();
    expect(error.isOperational).toBe(false);
  });
});

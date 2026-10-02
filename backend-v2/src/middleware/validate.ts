import { Request, Response, NextFunction } from 'express';
import { ZodSchema, ZodError } from 'zod';
import { sendError } from '../utils/response';

/**
 * Express middleware factory for request validation using zod schemas.
 *
 * Validates `req.body`, `req.query`, and/or `req.params` against the
 * provided schemas. On failure, returns a 422 with a machine-readable
 * VALIDATION_ERROR code and human-readable details.
 */
interface ValidateOptions {
  body?: ZodSchema;
  query?: ZodSchema;
  params?: ZodSchema;
}

export function validate(schemas: ValidateOptions) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const errors: string[] = [];

    if (schemas.body) {
      const result = schemas.body.safeParse(req.body);
      if (!result.success) {
        errors.push(...formatZodErrors(result.error, 'body'));
      } else {
        req.body = result.data;
      }
    }

    if (schemas.query) {
      const result = schemas.query.safeParse(req.query);
      if (!result.success) {
        errors.push(...formatZodErrors(result.error, 'query'));
      }
    }

    if (schemas.params) {
      const result = schemas.params.safeParse(req.params);
      if (!result.success) {
        errors.push(...formatZodErrors(result.error, 'params'));
      }
    }

    if (errors.length > 0) {
      sendError(res, 422, 'VALIDATION_ERROR', errors.join('; '));
      return;
    }

    next();
  };
}

function formatZodErrors(error: ZodError, source: string): string[] {
  return error.issues.map(
    (issue) => `${source}.${issue.path.join('.')}: ${issue.message}`,
  );
}

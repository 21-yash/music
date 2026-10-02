import { Response } from 'express';

/**
 * Consistent API response helpers.
 *
 * Success: { success: true,  data: T,    message: string | null }
 * Error:   { success: false, error: { code: string, message: string } }
 *
 * These match the contract defined in Claude.md.
 */

interface SuccessResponse<T> {
  success: true;
  data: T;
  message: string | null;
}

interface ErrorResponse {
  success: false;
  error: {
    code: string;
    message: string;
  };
}

export type ApiResponse<T> = SuccessResponse<T> | ErrorResponse;

export function sendSuccess<T>(
  res: Response,
  data: T,
  statusCode = 200,
  message: string | null = null,
): void {
  const body: SuccessResponse<T> = { success: true, data, message };
  res.status(statusCode).json(body);
}

export function sendError(
  res: Response,
  statusCode: number,
  code: string,
  message: string,
): void {
  const body: ErrorResponse = {
    success: false,
    error: { code, message },
  };
  res.status(statusCode).json(body);
}

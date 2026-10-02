import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Response } from 'express';
import { sendSuccess, sendError } from '../../src/utils/response';

function createMockResponse(): Response {
  const res = {
    status: vi.fn().mockReturnThis(),
    json: vi.fn().mockReturnThis(),
  } as unknown as Response;
  return res;
}

describe('sendSuccess', () => {
  let res: Response;

  beforeEach(() => {
    res = createMockResponse();
  });

  it('sends a 200 success response by default', () => {
    sendSuccess(res, { id: 1 });

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith({
      success: true,
      data: { id: 1 },
      message: null,
    });
  });

  it('supports custom status codes', () => {
    sendSuccess(res, { id: 1 }, 201);
    expect(res.status).toHaveBeenCalledWith(201);
  });

  it('supports a custom message', () => {
    sendSuccess(res, null, 200, 'Created successfully');

    expect(res.json).toHaveBeenCalledWith({
      success: true,
      data: null,
      message: 'Created successfully',
    });
  });
});

describe('sendError', () => {
  let res: Response;

  beforeEach(() => {
    res = createMockResponse();
  });

  it('sends the correct error response shape', () => {
    sendError(res, 404, 'NOT_FOUND', 'Resource not found');

    expect(res.status).toHaveBeenCalledWith(404);
    expect(res.json).toHaveBeenCalledWith({
      success: false,
      error: {
        code: 'NOT_FOUND',
        message: 'Resource not found',
      },
    });
  });
});

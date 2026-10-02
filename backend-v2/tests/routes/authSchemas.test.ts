import { describe, it, expect } from 'vitest';
import {
  registerSchema,
  loginSchema,
  refreshTokenSchema,
  logoutSchema,
  changePasswordSchema,
  deleteAccountSchema,
} from '../../src/routes/schemas/authSchemas';

describe('registerSchema', () => {
  it('accepts valid input', () => {
    const result = registerSchema.safeParse({
      username: 'testuser',
      email: 'test@example.com',
      password: 'password123',
    });
    expect(result.success).toBe(true);
  });

  it('rejects username shorter than 3 chars', () => {
    const result = registerSchema.safeParse({
      username: 'ab',
      email: 'test@example.com',
      password: 'password123',
    });
    expect(result.success).toBe(false);
  });

  it('rejects username with special characters', () => {
    const result = registerSchema.safeParse({
      username: 'test user!',
      email: 'test@example.com',
      password: 'password123',
    });
    expect(result.success).toBe(false);
  });

  it('allows underscores in username', () => {
    const result = registerSchema.safeParse({
      username: 'test_user_123',
      email: 'test@example.com',
      password: 'password123',
    });
    expect(result.success).toBe(true);
  });

  it('rejects invalid email', () => {
    const result = registerSchema.safeParse({
      username: 'testuser',
      email: 'not-an-email',
      password: 'password123',
    });
    expect(result.success).toBe(false);
  });

  it('rejects password shorter than 8 chars', () => {
    const result = registerSchema.safeParse({
      username: 'testuser',
      email: 'test@example.com',
      password: 'short',
    });
    expect(result.success).toBe(false);
  });
});

describe('loginSchema', () => {
  it('accepts valid input', () => {
    const result = loginSchema.safeParse({
      login: 'testuser',
      password: 'password123',
    });
    expect(result.success).toBe(true);
  });

  it('accepts optional deviceName', () => {
    const result = loginSchema.safeParse({
      login: 'testuser',
      password: 'password123',
      deviceName: 'My Phone',
    });
    expect(result.success).toBe(true);
  });

  it('rejects empty login', () => {
    const result = loginSchema.safeParse({
      login: '',
      password: 'password123',
    });
    expect(result.success).toBe(false);
  });
});

describe('refreshTokenSchema', () => {
  it('accepts valid input', () => {
    const result = refreshTokenSchema.safeParse({
      refreshToken: 'some-token-value',
      userId: 'user123',
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing userId', () => {
    const result = refreshTokenSchema.safeParse({
      refreshToken: 'some-token-value',
    });
    expect(result.success).toBe(false);
  });
});

describe('logoutSchema', () => {
  it('accepts valid input', () => {
    const result = logoutSchema.safeParse({
      refreshToken: 'some-token-value',
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing refreshToken', () => {
    const result = logoutSchema.safeParse({});
    expect(result.success).toBe(false);
  });
});

describe('changePasswordSchema', () => {
  it('accepts valid input', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'oldpassword',
      newPassword: 'newpassword123',
    });
    expect(result.success).toBe(true);
  });

  it('rejects short new password', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'oldpassword',
      newPassword: 'short',
    });
    expect(result.success).toBe(false);
  });
});

describe('deleteAccountSchema', () => {
  it('accepts valid input', () => {
    const result = deleteAccountSchema.safeParse({
      password: 'mypassword',
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing password', () => {
    const result = deleteAccountSchema.safeParse({});
    expect(result.success).toBe(false);
  });
});

import { connectMongoDB, disconnectMongoDB } from '../src/config/database';
import { Invite } from '../src/models/Invite';
import { generateRawInviteCode, hashInviteCode } from '../src/utils/invite';

interface Options {
  count: number;
  days?: number;
}

function parseArgs(args: string[]): Options {
  let count = 1;
  let days: number | undefined;

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--count' || arg === '-c') {
      const val = parseInt(args[++i], 10);
      if (isNaN(val) || val <= 0) {
        throw new Error('--count must be a positive integer');
      }
      count = val;
    } else if (arg === '--days' || arg === '-d') {
      const val = parseInt(args[++i], 10);
      if (isNaN(val) || val <= 0) {
        throw new Error('--days must be a positive integer');
      }
      days = val;
    }
  }

  return { count, days };
}

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));

  await connectMongoDB();

  const generatedCodes: string[] = [];
  const expiresAt = options.days
    ? new Date(Date.now() + options.days * 24 * 60 * 60 * 1000)
    : null;

  for (let i = 0; i < options.count; i++) {
    const rawCode = generateRawInviteCode();
    const codeHash = hashInviteCode(rawCode);

    await Invite.create({
      codeHash,
      expiresAt,
    });

    generatedCodes.push(rawCode);
  }

  /* eslint-disable no-console */
  console.log(`\nSuccessfully created ${generatedCodes.length} invite code(s):`);
  if (expiresAt) {
    console.log(`Expires at: ${expiresAt.toISOString()} (${options.days} days)`);
  } else {
    console.log('Expiration: Never expires');
  }
  console.log('----------------------------------------');
  for (const code of generatedCodes) {
    console.log(code);
  }
  console.log('----------------------------------------');
  console.log('NOTE: These codes are shown once and never stored in plain text.\n');
  /* eslint-enable no-console */

  await disconnectMongoDB();
}

main().catch(async (error) => {
  // eslint-disable-next-line no-console
  console.error('Failed to create invite codes:', error);
  try {
    await disconnectMongoDB();
  } catch {
    // Ignore cleanup error
  }
  process.exit(1);
});

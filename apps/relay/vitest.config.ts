import { defineWorkersConfig } from '@cloudflare/vitest-pool-workers/config';

export default defineWorkersConfig({
	test: {
		poolOptions: {
			workers: {
				// On Windows, miniflare/workerd SQLite locks prevent unlinking during isolatedStorage teardown.
				isolatedStorage: process.platform !== 'win32',
				wrangler: { configPath: './wrangler.toml' },
			},
		},
	},
});

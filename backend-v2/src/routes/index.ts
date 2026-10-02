import { Router } from 'express';
import healthRoutes from './health';
import authRoutes from './auth';
import musicRoutes from './music';

const router = Router();

// Mount sub-routers
router.use('/', healthRoutes);
router.use('/auth', authRoutes);
router.use('/music', musicRoutes);

export default router;

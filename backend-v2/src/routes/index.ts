import { Router } from 'express';
import healthRoutes from './health';
import authRoutes from './auth';
import musicRoutes from './music';
import sportsRoutes from './sports';
import libraryRoutes from './library';
import notificationRoutes from './notifications';

const router = Router();

// Mount sub-routers
router.use('/', healthRoutes);
router.use('/auth', authRoutes);
router.use('/music', musicRoutes);
router.use('/sports/cricket', sportsRoutes);
router.use('/library', libraryRoutes);
router.use('/notifications', notificationRoutes);

export default router;

import { Router } from 'express';
import { authenticate } from '../middleware/authenticate';
import { validate } from '../middleware/validate';
import * as notificationController from '../controllers/notificationController';
import {
  registerFcmTokenSchema,
  unregisterFcmTokenSchema,
  updateNotificationPreferencesSchema,
} from './schemas/notificationSchemas';

const router = Router();

router.use(authenticate);

router.get('/preferences', notificationController.getPreferences);
router.put(
  '/preferences',
  validate({ body: updateNotificationPreferencesSchema }),
  notificationController.updatePreferences,
);

router.put(
  '/token',
  validate({ body: registerFcmTokenSchema }),
  notificationController.registerToken,
);
router.delete(
  '/token',
  validate({ body: unregisterFcmTokenSchema }),
  notificationController.unregisterToken,
);
router.post(
  '/token/unregister',
  validate({ body: unregisterFcmTokenSchema }),
  notificationController.unregisterToken,
);

export default router;

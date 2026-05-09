/**
 * Barrel auth providers tiers.
 *
 * useAuth importe via ce barrel pour rester agnostique du provider
 * (un seul point a modifier si on ajoute Microsoft, Facebook, etc.).
 */

export {
  configureGoogleSignIn,
  signInWithGoogle,
  signOutGoogle,
  type GoogleIdTokenPayload,
} from './google';

export {
  APPLE_SIGN_IN_AVAILABLE,
  signInWithApple,
  signOutApple,
  type AppleIdTokenPayload,
} from './apple';

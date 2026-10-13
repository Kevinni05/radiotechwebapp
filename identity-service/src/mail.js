import nodemailer from 'nodemailer';
import { requireAccess } from './security.js';

export function mailer(config, env = process.env, http = fetch) {
  if (config.mailProvider === 'firebase') return {
    async send(message) {
      if (!message.invitationUrl) return; // Activation status remains available in the owner's independent audit.
      const link = new URL(message.invitationUrl);
      requireAccess(link.origin === config.origin && link.pathname === '/' && link.hash.startsWith('#invite='));
      const response = await http(`https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${encodeURIComponent(config.apiKey)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(15000),
        body: JSON.stringify({ requestType: 'PASSWORD_RESET', email: message.to, continueUrl: message.invitationUrl, canHandleCodeInApp: false }) });
      requireAccess(response.ok, 'Email non recapitata dal provider.', 503);
    }
  };
  const transport = nodemailer.createTransport({ host: env.IDENTITY_SMTP_HOST, port: Number(env.IDENTITY_SMTP_PORT || 465), secure: true,
    auth: { user: env.IDENTITY_SMTP_USER, pass: env.IDENTITY_SMTP_PASSWORD }, disableFileAccess: true, disableUrlAccess: true,
    connectionTimeout: 10000, greetingTimeout: 10000, socketTimeout: 20000 });
  return { send: message => transport.sendMail({ to: message.to, subject: message.subject, text: message.text, from: env.IDENTITY_MAIL_FROM }) };
}

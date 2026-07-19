# Product

## Register

product

## Users

Android users who install WeModern to modernize WeChat notifications, per-conversation chat bubbles, and call updates. They typically open the app briefly after installation or troubleshooting, and need to understand which system capabilities are active, which permissions still need attention, and what action to take next.

## Product Purpose

WeModern rewrites WeChat message and call notifications for newer Android versions. Message notifications can become native Android conversation bubbles, with a compact recent-message view and an explicit handoff to the original WeChat conversation. Bubble-ready messages use a low-importance quiet channel so the collapsed bubble preview, rather than a heads-up text notification, is the interruptive surface. The Bubble trampoline keeps one stable bubble for the latest conversation and opens WeChat Home inside it only after the user taps the bubble; its host notification is silent and minimized by default so it does not add a duplicate status-bar icon, while ordinary rewritten notifications continue to follow synchronous removal. The app UI exists to guide setup, verify that the notification service is working, expose optional reliability features, and provide lightweight tests. Success means a user can reach a working state without having to interpret Android permission terminology or duplicate status panels.

## Brand Personality

Reliable, light, and precise. The interface should feel native to modern Android, technically trustworthy, and calm enough for a utility that users visit only occasionally.

## Anti-references

Avoid dense developer dashboards, giant repeated cards, disabled controls used as status indicators, long walls of system terminology, excessive WeChat-green branding, and setup flows that make optional ADB features look mandatory.

## Design Principles

1. Lead with readiness and the single next useful action.
2. Explain permissions by outcome, then reveal Android terminology only when needed.
3. Separate required setup, recommended reliability improvements, and advanced ADB features.
4. Show each fact once; completed steps become compact status rows, not disabled buttons.
5. Keep testing available but visually secondary to setup and health.
6. Keep Android's None, Selected, and All bubble permission in Setup, distinct from WeModern's Chat bubbles feature switch. The system permission must be allowed before the feature switch can be enabled; dependent options remain hidden until both layers are ready, and normal notifications must remain useful when either layer is off.
7. Treat private/group bubble defaults and per-conversation overrides as primary settings in both normal and trampoline modes. Disabled conversations must retain heads-up notifications while being unable to create, update, or take over a bubble. Conversation notifications request full lock-screen visibility by default while respecting user-controlled Android settings. Clearly explain that conversation identity depends on the source name supplied by WeChat notifications.

## Accessibility & Inclusion

Use Android accessibility semantics, minimum 48 dp touch targets, scalable text, meaningful labels, and status cues that do not rely on color alone. Preserve sufficient contrast in dynamic and fallback color schemes, support dark theme, and respect reduced-motion preferences.

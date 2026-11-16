# NOTICE

AURA_2 is a beta fork of **Tempus** (https://github.com/eddyizm/tempus), which is a fork of Tempo by
CappielloAntonio. Tempus is licensed under the **GNU General Public License, version 3** (see `LICENSE`).

## License of AURA_2 during the beta

Temporary beta license: GNU GPL version 3 or later, the same as the code it is based on.
Anyone who receives the AURA_2 APK is entitled to the corresponding source code, which is this repository.
The additions in `app/src/main/java/com/eddyizm/tempus/hub/`, `ui/login/AuraConnectActivity.kt` and
`ui/fragment/HubFragment.kt` are released under the same terms.

A different license for the new files can be chosen later, but only for code that does not derive from
Tempus; the Tempus code itself cannot be relicensed away from the GPL.

## HomeMusic

AURA_2 talks to a HomeMusic server through its public HTTP API. One piece of HomeMusic code is ported:
the "My Wave" glow shader and its colour/smoothing logic (`web/src/lib/waveGlow.ts`,
`web/src/components/WaveGlow.tsx` of https://github.com/justman-1/HomeMusic-public), rewritten in
`app/src/main/java/com/eddyizm/tempus/ui/view/WaveGlowView.kt`. The HomeMusic repository carries no license
file, so this port needs its author's permission; without it, that file has to be replaced or removed.

## Third-party

All other libraries (AndroidX, Material, Media3, OkHttp, Retrofit, Glide and others) keep their own licenses.

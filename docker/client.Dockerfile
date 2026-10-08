# A TEST-ONLY static server for the client's production build.
#
# WHY THIS IS NOT A PRODUCTION DOCKERFILE, AND MUST NOT BECOME ONE
# The client deploys to Vercel, which builds it. Inventing a production image
# here would be a deployment decision disguised as a test decision. This exists
# for one reason: the compose stack needs the PRODUCTION BUILD served
# statically, because a dev server carries hot-module reloading and dev-only
# warnings, and neither of those is what ships.
#
# WHY IT TAKES A BUILT dist/ RATHER THAN BUILDING ONE
# This repository contains no path to a sibling repository -- that invariant is
# what lets the suite run in a pipeline where the application repositories are
# never checked out beside it. So the build context is supplied on the command
# line by whoever has the client checked out:
#
#   (in the client repository)  npm ci && VITE_API_BASE_URL= npm run build
#   (from this repository)      docker build -f docker/client.Dockerfile \
#                                 -t sasbo-online-client:local <that repo>/dist
#
# 🔴 THE EMPTY `VITE_API_BASE_URL` IS NOT OPTIONAL AND THIS RECIPE OMITTED IT for
# three days. `client/.env` is TRACKED and names the deployed backend; Vite inlines
# whatever is set at build time, so a plain `npm run build` bakes a bundle that
# calls the internet from inside the compose network. See the nginx note below for
# why same-origin is the design -- this is the same fact, stated where the command
# is, because a reader following the command never reaches the note.
#
# 🔑 PREFER `node tools/test-all.mjs`, WHICH CANNOT FORGET IT. These two commands
# are for a hand-driven stack; the orchestrator builds both images itself and
# supplies the variable in code. A comment cannot enforce this and did not.
#
# In a pipeline the same image comes from the client's own job instead, and
# nothing here changes -- ⚠️ including the requirement above, which that job also
# has to satisfy.

# 🔑 PINNED BY TAG AND DIGEST. `nginx:1.29-alpine` floated: a runner pulls fresh
# every time, so a moved tag changed the stack between two runs of the same three
# commits (the deploy arc's P6 review, 2026-09-21). The exact tag says what it is;
# the digest -- what `1.29-alpine` resolved to that day -- makes it immutable. Move
# both together, deliberately.
FROM nginx:1.29.8-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de

# The built assets, from whatever directory was given as the build context.
COPY . /usr/share/nginx/html

# Two things this config does, and both are load-bearing.
#
# ① SINGLE-PAGE-APPLICATION ROUTING. Every unknown path serves index.html so the
#    router can handle it. Without it, a scenario that navigates straight to a
#    deep link like /banks/listing gets nginx's own 404 and fails for a reason
#    that has nothing to do with the application.
#
# ② /api IS PROXIED TO THE SERVER, so the bundle can be built with an EMPTY
#    VITE_API_BASE_URL and call same-origin relative paths.
#
#    🔴 The alternative -- baking VITE_API_BASE_URL=http://server:8080 at build
#    time -- looks simpler and is a trap. Vite inlines that value into the
#    bundle, so the compiled client would carry a compose network hostname that
#    resolves inside the stack and NOWHERE else. A browser on the host, pointed
#    at the published port for a headed or debugging run, would fail every
#    request with a DNS error. Proxying keeps the bundle origin-agnostic, which
#    is the same property SASBO_CLIENT_URL / SASBO_API_URL give the suite.
#
# 🔴 THE PROXY FORWARDS `Host $http_host` -- PORT INCLUDED -- and the port is the fix.
#    Spring refuses a request whose `Origin` is foreign, but first decides WHETHER it
#    is foreign by comparing it with the request's own scheme, host and port. `$host`
#    drops the port, so a browser on the published client port (`npm run demo`)
#    reached the API looking like port 80, was judged cross-origin, and every save
#    got `403 Invalid CORS request`. `$http_host` keeps it: the demo and the
#    containerised suite (always same-origin) both work, and a genuinely foreign
#    Origin is still refused. tests/harness/invariants.spec.js holds this line.
#
#    ⚠️ Do not strip `Origin` here instead -- a first fix did, and it hid every CORS
#    refusal this stack would otherwise see (C10).
RUN printf '%s\n' \
    'server {' \
    '  listen 80;' \
    '  root /usr/share/nginx/html;' \
    '  index index.html;' \
    '  location /api/ { proxy_pass http://server:8080; proxy_set_header Host $http_host; }' \
    '  location / { try_files $uri $uri/ /index.html; }' \
    '}' > /etc/nginx/conf.d/default.conf

EXPOSE 80

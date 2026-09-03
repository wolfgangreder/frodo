# Kubernetes Deployment

## Plain kubectl

```bash
# 1. fill in placeholders in secret.yaml and configmap.yaml
# 2. apply
kubectl apply -f kubernetes/secret.yaml
kubectl apply -f kubernetes/configmap.yaml
kubectl apply -f kubernetes/deployment.yaml
```

Metrics endpoint: `http://<service>:8082/frodo/q/metrics`
Health: `http://<service>:8082/frodo/q/health/live`

### secret.yaml

Encode password before inserting:

```bash
echo -n "yourpassword" | base64
```

Paste result into `secret.yaml` → `QUARKUS_DATASOURCE_PASSWORD`.

### configmap.yaml

Fill in the Firebird JDBC URL and username the app should connect with.

---

## Helm

```bash
# copy and edit the example values
cp kubernetes/helm-values-example.yaml my-values.yaml
# edit my-values.yaml — fill in host, jdbcUrl, user

# install
helm upgrade --install frodo helm/frodo \
  -f my-values.yaml \
  --set firebird.password="yourpassword" \
  --namespace default --create-namespace

# uninstall
helm uninstall frodo --namespace default
```

Pass `--set firebird.password=...` on the command line to avoid storing the password in a file.

### Available values

| Key | Default | Description |
|---|---|---|
| `image.tag` | `2.1.3-SNAPSHOT` | Container image tag |
| `firebird.jdbcUrl` | see values.yaml | Jaybird JDBC URL |
| `firebird.user` | `sysdba` | DB user |
| `firebird.password` | `CHANGEME` | DB password — always override |
| `ingress.enabled` | `false` | Create Ingress resource |
| `ingress.className` | `""` | IngressClass (e.g. `nginx`, `traefik`) |
| `prometheus.scrape` | `true` | Add `prometheus.io/scrape` pod annotation |

Full list: [`helm/frodo/values.yaml`](../helm/frodo/values.yaml)

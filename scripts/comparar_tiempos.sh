#!/usr/bin/env bash
# Compara la version SIN hilos (bloqueante) con la version CON hilos (1, 2, 4, 8, 12 hilos).
#   ./scripts/comparar_tiempos.sh [http://localhost:8080]
# Requiere: curl y python
set -euo pipefail
URL="${1:-http://localhost:8080}"

campo() { python -c "import sys,json; print(json.load(sys.stdin)$1)"; }

printf '%-22s %18s %16s %10s\n' "modo" "respuesta HTTP" "calculo" "speedup"
r=$(curl -s -w '\n%{time_total}' "$URL/api/estadisticas/sin-hilos")
http=$(tail -n1 <<< "$r"); base=$(head -n -1 <<< "$r" | campo "['msProcesamiento']")
printf '%-22s %16.0f ms %13s ms %10s\n' "sin hilos (bloqueante)" "$(python -c "print($http*1000)")" "$base" "1.00x"

for h in 1 2 4 8 12; do
  r=$(curl -s -w '\n%{time_total}' -X POST "$URL/api/estadisticas?hilos=$h")
  http=$(tail -n1 <<< "$r"); job=$(head -n -1 <<< "$r" | campo "['jobId']")
  until curl -s "$URL/api/estadisticas/ultimo" | grep -q "\"jobId\":\"$job\""; do sleep 0.2; done
  ms=$(curl -s "$URL/api/estadisticas/ultimo" | campo "['msProcesamiento']")
  printf '%-22s %16.0f ms %13s ms %9.2fx\n' "con $h hilo(s)" "$(python -c "print($http*1000)")" "$ms" "$(python -c "print($base/$ms)")"
done

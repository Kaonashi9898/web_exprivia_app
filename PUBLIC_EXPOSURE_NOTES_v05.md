# prenotazioniExpBA v05 - esposizione pubblica via Apache

Obiettivo pubblico:

```text
https://prenotazioniexpba.exptraining.it:8082
```

Flusso desiderato:

```text
Internet
→ prenotazioniexpba.exptraining.it:8082
→ FRITZ!Box
→ MacBookPro 192.168.178.31:8444
→ Apache2
→ Docker frontend/API
```

## Differenza principale rispetto alla v04

La v04, essendo avviata con `ng serve` in configurazione `development`, usa sul browser i fallback:

```text
http://localhost:13030
http://localhost:13040
http://localhost:13050
http://localhost:13020/editor
```

Questo funziona sul MacBook in locale, ma non funziona da internet: il `localhost` del browser remoto non è il MacBook.

La v05 mantiene le URL localhost solo quando il browser sta realmente usando `localhost` o `127.0.0.1`; in tutti gli altri casi usa path relativi:

```text
/api-utenti
/api-location
/api-prenotazioni
/editor/
```

Apache deve quindi instradare questi path verso le porte Docker locali.

## File Apache incluso

Nel pacchetto è incluso:

```text
APACHE_prenotazioniExpBA-8444.conf
```

Copia suggerita:

```bash
sudo cp APACHE_prenotazioniExpBA-8444.conf /etc/apache2/sites-available/prenotazioniExpBA-8444.conf
sudo a2ensite prenotazioniExpBA-8444.conf
sudo apache2ctl configtest
sudo systemctl restart apache2
```

## Test Apache locali

```bash
curl -k -I https://localhost:8444/ -H "Host: prenotazioniexpba.exptraining.it"
curl -k -I https://localhost:8444/api-utenti/api/auth/login -H "Host: prenotazioniexpba.exptraining.it"
curl -k -I https://localhost:8444/editor/ -H "Host: prenotazioniexpba.exptraining.it"
```

Nota: l'endpoint `/api/auth/login` è POST, quindi con `curl -I` potrebbe rispondere `405 Method Not Allowed`; questo è comunque un segnale utile perché dimostra che Apache sta raggiungendo il servizio corretto.

## DNS/IONOS

Record desiderato:

```text
A prenotazioniexpba 93.46.59.130
```

Rimuovere o disattivare eventuale record `AAAA` non gestito, per evitare che alcuni client provino IPv6 verso IONOS invece dell'IP pubblico corretto.

## FRITZ!Box

Regola desiderata:

```text
porta esterna 8082 TCP
→ MacBookPro 192.168.178.31
→ porta interna 8444 TCP
```

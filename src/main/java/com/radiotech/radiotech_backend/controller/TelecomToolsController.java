package com.radiotech.radiotech_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({ "/api/telecom-tools", "/api/v1/telecom-tools" })
public class TelecomToolsController {
    private static final double C = 299_792_458.0;
    private static final double K = 1.380649e-23;
    private static final double T = 290.0;

    @PostMapping("/fspl")
    public ResponseEntity<?> fspl(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double fMHz = req(b, "frequencyMHz"), dKm = req(b, "distanceKm");
        if (fMHz <= 0 || dKm <= 0)
            return bad("Frequenza e distanza devono essere positive.");
        double value = 32.44 + 20 * Math.log10(fMHz) + 20 * Math.log10(dKm);
        return ok("fsplDb", value, "formula", "32.44 + 20 log10(f_MHz) + 20 log10(d_km)");
    }

    @PostMapping("/eirp")
    public ResponseEntity<?> eirp(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double tx = req(b, "txPowerDbm"), gain = req(b, "antennaGainDbi"), loss = req(b, "cableLossDb");
        double value = tx + gain - loss;
        return ok("eirpDbm", value, "eirpW", Math.pow(10, (value - 30) / 10), "formula",
                "EIRP_dBm = P_TX_dBm + G_dBi - L_dB");
    }

    @PostMapping("/link-budget")
    public ResponseEntity<?> linkBudget(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double f = req(b, "frequencyMHz"), d = req(b, "distanceKm"), tx = req(b, "txPowerDbm"),
                gt = req(b, "txGainDbi"), gr = req(b, "rxGainDbi"), lt = req(b, "txCableLossDb"),
                lr = req(b, "rxCableLossDb"), other = n(b, "otherLossDb", 0),
                sensitivity = n(b, "rxSensitivityDbm", Double.NaN);
        if (f <= 0 || d <= 0)
            return bad("Frequenza e distanza devono essere positive.");
        double fspl = 32.44 + 20 * Math.log10(f) + 20 * Math.log10(d);
        double eirp = tx + gt - lt;
        double received = eirp - fspl + gr - lr - other;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fsplDb", fspl);
        out.put("eirpDbm", eirp);
        out.put("receivedPowerDbm", received);
        if (Double.isFinite(sensitivity))
            out.put("linkMarginDb", received - sensitivity);
        out.put("rssiEstimatedDbm", received);
        out.put("formula", "P_RX = P_TX + G_TX + G_RX - L_TX - FSPL - L_RX - L_OTHER");
        return ResponseEntity.ok(out);
    }

    @PostMapping("/power")
    public ResponseEntity<?> power(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        if (b.get("dbm") != null) {
            double dbm = b.get("dbm");
            double mw = Math.pow(10, dbm / 10);
            return ok("mw", mw, "w", mw / 1000, "dbm", dbm);
        }
        double w = req(b, "w");
        if (w <= 0)
            return bad("La potenza deve essere positiva.");
        double mw = w * 1000, dbm = 10 * Math.log10(mw);
        return ok("dbm", dbm, "mw", mw, "w", w);
    }

    @PostMapping("/wavelength")
    public ResponseEntity<?> wavelength(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double hz = n(b, "frequencyHz", Double.NaN);
        if (!Double.isFinite(hz))
            hz = req(b, "frequencyMHz") * 1_000_000;
        if (hz <= 0)
            return bad("Frequenza non valida.");
        return ok("wavelengthM", C / hz, "frequencyHz", hz);
    }

    @PostMapping("/vswr")
    public ResponseEntity<?> vswr(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        if (b.get("vswr") != null) {
            double v = req(b, "vswr");
            if (v < 1)
                return bad("VSWR deve essere >= 1.");
            double gamma = (v - 1) / (v + 1);
            return ok("reflectionCoefficient", gamma, "returnLossDb", -20 * Math.log10(gamma), "vswr", v);
        }
        double gamma = req(b, "reflectionCoefficient");
        if (gamma < 0 || gamma >= 1)
            return bad("Il coefficiente di riflessione deve essere tra 0 e 1.");
        return ok("vswr", (1 + gamma) / (1 - gamma), "returnLossDb", gamma == 0 ? 99.0 : -20 * Math.log10(gamma));
    }

    @PostMapping("/snr")
    public ResponseEntity<?> snr(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double bw = req(b, "bandwidthHz"), nf = n(b, "noiseFigureDb", 0), signal = req(b, "signalDbm");
        if (bw <= 0)
            return bad("Bandwidth non valida.");
        double noise = -174 + 10 * Math.log10(bw) + nf;
        return ok("noiseFloorDbm", noise, "snrDb", signal - noise, "formula", "N_dBm = -174 + 10 log10(B_Hz) + NF_dB");
    }

    @PostMapping("/fresnel")
    public ResponseEntity<?> fresnel(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double fGHz = req(b, "frequencyGHz"), d1 = req(b, "distance1Km"), d2 = req(b, "distance2Km");
        if (fGHz <= 0 || d1 <= 0 || d2 <= 0)
            return bad("Frequenza e distanze devono essere positive.");
        double lambda = C / (fGHz * 1e9), r = Math.sqrt(lambda * (d1 * 1000) * (d2 * 1000) / (d1 * 1000 + d2 * 1000));
        return ok("radiusM", r, "formula", "r = sqrt(lambda*d1*d2/(d1+d2))");
    }

    @PostMapping("/cable-loss")
    public ResponseEntity<?> cableLoss(@RequestBody Map<String, Double> b) {
        if (b == null)
            return bad("Body della richiesta obbligatorio.");
        double length = req(b, "lengthM"), attenuation = req(b, "attenuationDbPer100m"),
                connectors = n(b, "connectorCount", 0), connectorLoss = n(b, "connectorLossDb", 0),
                splice = n(b, "spliceCount", 0), spliceLoss = n(b, "spliceLossDb", 0);
        if (length < 0 || attenuation < 0)
            return bad("Lunghezza e attenuazione non possono essere negative.");
        return ok("lossDb", attenuation * length / 100 + connectors * connectorLoss + splice * spliceLoss, "formula",
                "L = alpha*length/100 + N_conn*L_conn + N_splice*L_splice");
    }

    private double req(Map<String, Double> b, String k) {
        Double v = b.get(k);
        if (v == null || !Double.isFinite(v))
            throw new IllegalArgumentException("Parametro obbligatorio: " + k);
        return v;
    }

    private double n(Map<String, Double> b, String k, double d) {
        Double v = b.get(k);
        return v == null ? d : v;
    }

    private ResponseEntity<?> ok(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        return ResponseEntity.ok(m);
    }

    private ResponseEntity<?> bad(String s) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", s));
    }
}

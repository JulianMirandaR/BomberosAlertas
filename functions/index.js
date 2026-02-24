const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.enviarAlertaFCM = onDocumentCreated(
  "alertas/{alertaId}",
  async (event) => {
    try {
      const alertData = event.data.data();
      const codigoAlerta = alertData.codigoCuartel || ""; // Si no hay código, asumimos string vacío

      console.log(`Procesando alerta. Código de grupo: "${codigoAlerta}"`);

      // Filtrado ESTRICTO: Solo enviar a dispositivos que coincidan exactamente con el código
      let query = admin.firestore().collection("tokens")
        .where("codigoCuartel", "==", codigoAlerta);

      const snapshot = await query.get();

      if (snapshot.empty) {
        console.log("No hay tokens registrados para este grupo");
        return;
      }

      // 📩 Payload
      const senderUid = alertData.senderUid;

      const tokens = snapshot.docs
        .filter(doc => {
          const tokenData = doc.data();
          // Si el token pertenece al que envió la alerta, lo ignoramos
          if (senderUid && tokenData.uid === senderUid) {
            return false;
          }
          return true;
        })
        .map(doc => doc.data().token)
        .filter(token => token);

      if (tokens.length === 0) {
        console.log("Tokens vacíos (o solo el del remitente)");
        return;
      }

      const titulo = alertData.titulo || "🚨 ALERTA DE INCENDIO";

      const message = {
        android: {
          priority: "high"
        },
        data: {
          title: titulo,
          body: "Se activó una nueva alerta. Verificá la app.",
          alertaId: event.params.alertaId
        },
        tokens
      };

      const response = await admin.messaging().sendEachForMulticast(message);

      console.log(
        "Notificaciones enviadas:",
        response.successCount,
        "ok /",
        response.failureCount,
        "fallidas"
      );

    } catch (error) {
      console.error("Error enviando FCM:", error);
    }
  }
);

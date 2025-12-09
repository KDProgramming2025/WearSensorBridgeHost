mv /tmp/wear-server.service /etc/systemd/system/wear-server.service
systemctl daemon-reload
systemctl enable wear-server
systemctl start wear-server
systemctl status wear-server


// for debugging and shader feature development purposes

varying vec4 worldPos;

uniform sampler2D m_DiffuseMap;

// tiles
void main(){
    vec2 newTexCoord;
    if (mod(worldPos.x, 1) == 0) newTexCoord = vec2(mod(worldPos.z, 1), mod(worldPos.y, 1));
    else if (mod(worldPos.y, 1) == 0) newTexCoord = vec2(mod(worldPos.x, 1), mod(worldPos.z, 1));
    else newTexCoord = vec2(mod(worldPos.x, 1), mod(worldPos.y, 1));

    vec4 diffuseColor = texture2D(m_DiffuseMap, newTexCoord);

    gl_FragColor = diffuseColor;
}

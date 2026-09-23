--[[
	Plant: a thin stem with a thick base, blades springing from low on the stem.

	Its own model at the top level, NOT inside Camp, so it moves and deletes
	independently. PrimaryPart is the base segment, so the pivot sits on the
	ground rather than in the middle of the bounding box.

	Shape controls, in the order worth turning:
	  SEGMENTS   the taper. First entry is the thick base.
	  BLADES     how full it looks.
	  up         where each blade attaches; keep it inside the first segment.
	  tilt/len   both derive from up: low blades are longer and more splayed,
	             which is what gives a fountain shape rather than a pom-pom.
]]

local AT = Vector3.new(-10, 0.5, 2)      -- where it stands; floor top is y = 0.5
local BLADES = 8
local SEGMENTS = {{h = 1.4, d = 1.5}, {h = 1.5, d = 0.85}, {h = 1.5, d = 0.5}}

local plant = Instance.new("Model"); plant.Name = "Plant"; plant.Parent = workspace

local function piece(name, shape, size, cf, colour, material)
	local p = Instance.new("Part")
	p.Name, p.Shape, p.Anchored, p.Size, p.CFrame = name, shape, true, size, cf
	p.Color, p.Material, p.CanCollide = colour, material, false
	p.TopSurface, p.BottomSurface = Enum.SurfaceType.Smooth, Enum.SurfaceType.Smooth
	p.Parent = plant
	return p
end

-- stem: stacked cylinders, thick at the base, thinning upward
local y = 0
for i, s in ipairs(SEGMENTS) do
	local stem = piece("Stem" .. i, Enum.PartType.Cylinder, Vector3.new(s.h, s.d, s.d),
		CFrame.new(AT + Vector3.new(0, y + s.h / 2, 0))
			* CFrame.Angles(0, 0, math.rad(90))
			* CFrame.Angles(math.rad(math.random(-4, 4)), 0, math.rad(math.random(-4, 4))),
		Color3.fromRGB(62, 58, 40), Enum.Material.Wood)
	if i == 1 then plant.PrimaryPart = stem end
	y = y + s.h
end

-- blades: springing from the thick lower stem, lower ones longer and more splayed
for i = 1, BLADES do
	local yaw = math.rad((i / BLADES) * 360 + math.random(-14, 14))
	local up = 0.25 + (i % 3) * 0.45 + math.random() * 0.25   -- 0.25 to ~1.6 studs up
	local tilt = math.rad(70 - up * 22 + math.random(-6, 6))  -- low = splayed, high = upright
	local len = 3.6 - up * 0.7 + math.random() * 0.8          -- low = longer
	piece("Blade" .. i, Enum.PartType.Block, Vector3.new(0.12, len, 0.55),
		CFrame.new(AT + Vector3.new(0, up, 0))
			* CFrame.Angles(0, yaw, 0)
			* CFrame.Angles(0, 0, tilt)
			* CFrame.new(0, len / 2, 0),
		Color3.fromRGB(46, 62, 38), Enum.Material.Grass)
end
